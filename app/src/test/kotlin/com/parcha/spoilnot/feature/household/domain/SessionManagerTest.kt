package com.parcha.spoilnot.feature.household.domain

import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.feature.auth.domain.AuthRepository
import com.parcha.spoilnot.feature.auth.domain.AuthUser
import com.parcha.spoilnot.feature.auth.domain.PendingJoin
import com.parcha.spoilnot.feature.auth.domain.UserProfile
import com.parcha.spoilnot.feature.auth.domain.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
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

    /** Calls to auth and local data, in order. */
    private val events = mutableListOf<String>()
    private val auth = FakeAuth(events)
    private val users = FakeUsers()
    private val households = FakeHouseholds(users)
    private val localData = FakeLocalData(events)

    private fun TestScope.manager(): SessionManager =
        SessionManager(auth, users, households, localData, backgroundScope).also { advanceUntilIdle() }

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
        assertEquals(listOf("clear", "signOut"), events)
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

    private fun request(status: JoinRequestStatus) = JoinRequest("sam", "Sam", pending.code, status, null)

    private class FakeAuth(private val log: MutableList<String>) : AuthRepository {
        val user = MutableStateFlow<AuthUser?>(null)
        var signUpResult: AuthUser? = null
        override val authUser: Flow<AuthUser?> = user
        override val currentUser: AuthUser? get() = user.value

        override suspend fun signUp(displayName: String, email: String, password: String) {
            user.value = signUpResult
        }

        override suspend fun signIn(email: String, password: String) = Unit

        override suspend fun sendPasswordReset(email: String) = Unit

        override suspend fun sendEmailVerification() = Unit

        override suspend fun reload(): AuthUser? = user.value

        override suspend fun verifyPassword(password: String) {
            if (password != "password1") throw AppError.InvalidCredentials()
        }

        override suspend fun reauthenticate(password: String) = verifyPassword(password)

        override fun signOut() {
            log += "signOut"
            user.value = null
        }

        override suspend fun deleteAccount() {
            log += "delete"
            user.value = null
        }
    }

    /** Server state of users/{uid}; null means the document does not exist. */
    private class FakeUsers : UserRepository {
        private val docs = mutableMapOf<String, MutableStateFlow<UserProfile?>>()
        val deleted = mutableSetOf<String>()

        fun doc(uid: String) = docs.getOrPut(uid) { MutableStateFlow(null) }

        fun put(profile: UserProfile) {
            doc(profile.uid).value = profile
        }

        fun profile(uid: String) = doc(uid).value

        override fun observeProfile(uid: String): Flow<UserProfile?> = doc(uid)

        override suspend fun createProfileIfMissing(uid: String, displayName: String) {
            if (doc(uid).value == null) doc(uid).value = UserProfile(uid, displayName, null, null)
        }

        override suspend fun clearHousehold(uid: String) {
            doc(uid).value = doc(uid).value?.copy(householdId = null)
        }

        override suspend fun deleteProfile(uid: String) {
            deleted += uid
            doc(uid).value = null
        }
    }

    private class FakeHouseholds(private val users: FakeUsers) : HouseholdRepository {
        private val memberships = mutableMapOf<Pair<String, String>, MutableStateFlow<Membership?>>()
        val ownRequest = MutableStateFlow<JoinRequest?>(null)

        fun membership(hid: String, uid: String) = memberships.getOrPut(hid to uid) { MutableStateFlow(null) }

        override fun observeMembership(householdId: String, uid: String): Flow<Membership> =
            membership(householdId, uid).filterNotNull()

        override fun observeOwnJoinRequest(householdId: String, uid: String): Flow<JoinRequest?> = ownRequest.map { it }

        override suspend fun completeJoin(uid: String, pending: PendingJoin) {
            users.put(UserProfile(uid, users.profile(uid)!!.displayName, pending.householdId, null))
            ownRequest.value = null
        }

        override suspend fun abandonJoin(uid: String, pending: PendingJoin) {
            users.put(users.profile(uid)!!.copy(pendingJoin = null))
            ownRequest.value = null
        }

        override suspend fun leave(householdId: String, uid: String) {
            membership(householdId, uid).value = Membership.Gone
            users.clearHousehold(uid)
        }

        override fun observeHousehold(householdId: String): Flow<Household?> = error("unused")

        override fun observeMembers(householdId: String): Flow<List<Member>> = error("unused")

        override fun observeJoinRequests(householdId: String): Flow<List<JoinRequest>> = error("unused")

        override fun observeInviteCode(code: String): Flow<InviteCode?> = error("unused")

        override suspend fun createHousehold(uid: String, displayName: String, name: String, inviteCode: String) =
            error("unused")

        override suspend fun findInviteCode(code: String): InviteCode? = error("unused")

        override suspend fun requestToJoin(uid: String, displayName: String, invite: InviteCode) = error("unused")

        override suspend fun approve(householdId: String, request: JoinRequest) = error("unused")

        override suspend fun decline(householdId: String, requesterUid: String) = error("unused")

        override suspend fun rename(household: Household, newName: String) = error("unused")

        override suspend fun regenerateInviteCode(household: Household, uid: String, newCode: String, active: Boolean) =
            error("unused")

        override suspend fun setJoiningPaused(code: String, paused: Boolean) = error("unused")

        override suspend fun removeMember(householdId: String, memberUid: String) = error("unused")

        override suspend fun transferOwnership(householdId: String, fromUid: String, toUid: String) = error("unused")

        override suspend fun deleteHousehold(household: Household, uid: String) = error("unused")
    }

    private class FakeLocalData(private val log: MutableList<String>) : LocalData {
        var clears = 0

        override suspend fun clear() {
            clears++
            log += "clear"
        }
    }
}
