package com.parcha.spoilnot.feature.household.domain

import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.feature.auth.domain.AuthUser
import com.parcha.spoilnot.feature.auth.domain.PendingJoin
import com.parcha.spoilnot.feature.auth.domain.UserProfile
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {
    private val sam = AuthUser("sam", "sam@example.com", "Sam", emailVerified = false)
    private val pending = PendingJoin("h1", "The Smiths", "ABCDE23456")

    /** Calls to auth, devices and local data, in order. */
    private val events = mutableListOf<String>()
    private val auth = FakeAuth(events)
    private val users = FakeUsers()
    private val households = FakeHouseholds(users)
    private val devices = FakeDevices(events)
    private val localData = FakeLocalData(events)

    private fun TestScope.manager(): SessionManager =
        SessionManager(auth, users, households, localData, devices, backgroundScope).also { advanceUntilIdle() }

    private fun runSession(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher()) { block() }

    @Test
    fun `signed out without an account`() = runSession {
        val manager = manager()
        assertEquals(Session.SignedOut, manager.session.value)
    }

    @Test
    fun `a missing profile is created with the sign-up name, then the gate is shown`() = runSession {
        val manager = manager()
        auth.signUpResult = sam.copy(displayName = null)
        manager.signUp(" Samantha ", "sam@example.com", "password1")
        advanceUntilIdle()

        assertEquals("Samantha", users.profile("sam")?.displayName)
        assertEquals(Session.NoHousehold(sam.copy(displayName = null), "Samantha", null), manager.session.value)
    }

    @Test
    fun `a member of a household is in it with their role`() = runSession {
        users.put(UserProfile("sam", "Sam", "h1", null))
        households.membership("h1", "sam").value = Membership.Active(Role.Owner)
        auth.user.value = sam
        val manager = manager()

        assertEquals(Session.InHousehold(sam, "Sam", "h1", Role.Owner), manager.session.value)
        assertEquals(0, localData.clears)
    }

    @Test
    fun `losing the membership returns to the gate and clears local data once`() = runSession {
        users.put(UserProfile("sam", "Sam", "h1", null))
        households.membership("h1", "sam").value = Membership.Active(Role.Member)
        auth.user.value = sam
        val manager = manager()

        households.membership("h1", "sam").value = Membership.Gone
        advanceUntilIdle()

        assertEquals(Session.NoHousehold(sam, "Sam", null), manager.session.value)
        assertNull(users.profile("sam")?.householdId)
        assertEquals(1, localData.clears)
    }

    @Test
    fun `a membership already gone at start still clears the stale pointer and cache`() = runSession {
        users.put(UserProfile("sam", "Sam", "h1", null))
        households.membership("h1", "sam").value = Membership.Gone
        auth.user.value = sam
        val manager = manager()

        assertEquals(Session.NoHousehold(sam, "Sam", null), manager.session.value)
        assertNull(users.profile("sam")?.householdId)
        assertEquals(1, localData.clears)
    }

    @Test
    fun `leaving returns to the gate and clears local data once`() = runSession {
        users.put(UserProfile("sam", "Sam", "h1", null))
        households.membership("h1", "sam").value = Membership.Active(Role.Member)
        auth.user.value = sam
        val manager = manager()

        households.leave("h1", "sam")
        advanceUntilIdle()

        assertEquals(Session.NoHousehold(sam, "Sam", null), manager.session.value)
        assertEquals(1, localData.clears)
    }

    @Test
    fun `a waiting, declined or vanished join request is reported`() = runSession {
        users.put(UserProfile("sam", "Sam", null, pending))
        households.ownRequest.value = request(JoinRequestStatus.Pending)
        auth.user.value = sam
        val manager = manager()
        assertEquals(JoinState.Waiting(pending), (manager.session.value as Session.NoHousehold).join)

        households.ownRequest.value = request(JoinRequestStatus.Declined)
        advanceUntilIdle()
        assertEquals(JoinState.Declined(pending), (manager.session.value as Session.NoHousehold).join)

        households.ownRequest.value = null
        advanceUntilIdle()
        assertEquals(JoinState.Expired(pending), (manager.session.value as Session.NoHousehold).join)
    }

    @Test
    fun `an approved request completes on its own and enters the household`() = runSession {
        users.put(UserProfile("sam", "Sam", null, pending))
        households.ownRequest.value = request(JoinRequestStatus.Pending)
        auth.user.value = sam
        val manager = manager()

        households.membership("h1", "sam").value = Membership.Active(Role.Member)
        households.ownRequest.value = request(JoinRequestStatus.Approved)
        advanceUntilIdle()

        assertEquals(Session.InHousehold(sam, "Sam", "h1", Role.Member), manager.session.value)
        assertEquals(UserProfile("sam", "Sam", "h1", null), users.profile("sam"))
        assertEquals(0, localData.clears)
    }

    @Test
    fun `sign-out clears local data before signing out`() = runSession {
        users.put(UserProfile("sam", "Sam", "h1", null))
        households.membership("h1", "sam").value = Membership.Active(Role.Member)
        auth.user.value = sam
        val manager = manager()

        manager.signOut()
        advanceUntilIdle()

        assertEquals(Session.SignedOut, manager.session.value)
        assertEquals(listOf("unregister sam", "clear", "signOut"), events)
    }

    @Test
    fun `sign-out finishes when the screen that asked for it is closed`() = runSession {
        users.put(UserProfile("sam", "Sam", "h1", null))
        households.membership("h1", "sam").value = Membership.Active(Role.Member)
        auth.user.value = sam
        val manager = manager()
        val server = CompletableDeferred<Unit>().also { devices.unregisterGate = it }

        // Settings' coroutine: the session turns Loading, which removes the screen and cancels it.
        val screen = launch { manager.signOut() }
        assertEquals(Session.Loading, manager.session.value)
        screen.cancel()
        server.complete(Unit)
        advanceUntilIdle()

        assertTrue(screen.isCancelled)
        assertEquals(listOf("unregister sam", "clear", "signOut"), events)
        assertEquals(Session.SignedOut, manager.session.value)
    }

    @Test
    fun `a failed device delete still clears local data and signs out`() = runSession {
        users.put(UserProfile("sam", "Sam", "h1", null))
        households.membership("h1", "sam").value = Membership.Active(Role.Member)
        auth.user.value = sam
        val manager = manager()
        devices.unregisterError = IOException("offline")

        manager.signOut()
        advanceUntilIdle()

        assertEquals(listOf("clear", "signOut"), events)
        assertEquals(Session.SignedOut, manager.session.value)
    }

    @Test
    fun `cancelling the application scope stops sign-out instead of skipping a step`() = runSession {
        users.put(UserProfile("sam", "Sam", "h1", null))
        households.membership("h1", "sam").value = Membership.Active(Role.Member)
        auth.user.value = sam
        val appScope = CoroutineScope(coroutineContext + SupervisorJob())
        val manager = SessionManager(auth, users, households, localData, devices, appScope)
        devices.unregisterGate = CompletableDeferred()

        val caller = launch { runCatching { manager.signOut() } }
        appScope.cancel()
        advanceUntilIdle()

        // Nothing after the cancelled step ran: a cleanup step never swallows cancellation.
        assertTrue(caller.isCompleted)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `an owner cannot delete their account`() = runSession {
        users.put(UserProfile("sam", "Sam", "h1", null))
        households.membership("h1", "sam").value = Membership.Active(Role.Owner)
        auth.user.value = sam
        val manager = manager()

        try {
            manager.deleteAccount("password1")
            fail("Expected OwnerMustTransferFirst")
        } catch (e: AppError.OwnerMustTransferFirst) {
            // expected
        }
        assertEquals(sam, auth.user.value)
        assertTrue(users.profile("sam") != null)
    }

    @Test
    fun `a wrong password deletes nothing`() = runSession {
        users.put(UserProfile("sam", "Sam", null, null))
        auth.user.value = sam
        val manager = manager()

        try {
            manager.deleteAccount("wrong")
            fail("Expected InvalidCredentials")
        } catch (e: AppError.InvalidCredentials) {
            // expected
        }
        assertTrue(users.profile("sam") != null)
    }

    @Test
    fun `deleting a member's account leaves, deletes the profile, clears data and deletes the sign-in`() = runSession {
        users.put(UserProfile("sam", "Sam", "h1", null))
        households.membership("h1", "sam").value = Membership.Active(Role.Member)
        auth.user.value = sam
        val manager = manager()

        manager.deleteAccount("password1")
        advanceUntilIdle()

        assertEquals(Membership.Gone, households.membership("h1", "sam").value)
        assertTrue(users.deleted.contains("sam"))
        assertEquals(1, localData.clears)
        assertEquals(Session.SignedOut, manager.session.value)
        // The profile must not be recreated while the account is being deleted.
        assertNull(users.profile("sam"))
    }

    @Test
    fun `account deletion finishes when the screen that asked for it is closed`() = runSession {
        users.put(UserProfile("sam", "Sam", "h1", null))
        households.membership("h1", "sam").value = Membership.Active(Role.Member)
        auth.user.value = sam
        val manager = manager()
        val server = CompletableDeferred<Unit>().also { households.leaveGate = it }

        val screen = launch { manager.deleteAccount("password1") }
        assertEquals(Session.Loading, manager.session.value)
        screen.cancel()
        server.complete(Unit)
        advanceUntilIdle()

        assertTrue(screen.isCancelled)
        assertEquals(Membership.Gone, households.membership("h1", "sam").value)
        assertTrue(users.deleted.contains("sam"))
        assertEquals(listOf("clear", "delete"), events)
        assertEquals(Session.SignedOut, manager.session.value)
    }

    @Test
    fun `closing the screen during the password check deletes nothing`() = runSession {
        users.put(UserProfile("sam", "Sam", "h1", null))
        households.membership("h1", "sam").value = Membership.Active(Role.Member)
        auth.user.value = sam
        val manager = manager()
        val check = CompletableDeferred<Unit>().also { auth.verifyGate = it }

        val screen = launch { manager.deleteAccount("password1") }
        screen.cancel()
        check.complete(Unit)
        advanceUntilIdle()

        assertEquals(Membership.Active(Role.Member), households.membership("h1", "sam").value)
        assertTrue(users.deleted.isEmpty())
        assertEquals(emptyList<String>(), events)
        assertEquals(Session.InHousehold(sam, "Sam", "h1", Role.Member), manager.session.value)
    }

    private fun request(status: JoinRequestStatus) = JoinRequest("sam", "Sam", pending.code, status, null)
}
