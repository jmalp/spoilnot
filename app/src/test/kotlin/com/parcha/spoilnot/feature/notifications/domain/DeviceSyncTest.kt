package com.parcha.spoilnot.feature.notifications.domain

import com.parcha.spoilnot.feature.auth.domain.AuthUser
import com.parcha.spoilnot.feature.auth.domain.UserProfile
import com.parcha.spoilnot.feature.household.domain.FakeAuth
import com.parcha.spoilnot.feature.household.domain.FakeDevices
import com.parcha.spoilnot.feature.household.domain.FakeHouseholds
import com.parcha.spoilnot.feature.household.domain.FakeLocalData
import com.parcha.spoilnot.feature.household.domain.FakeUsers
import com.parcha.spoilnot.feature.household.domain.Membership
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.feature.household.domain.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSyncTest {
    private val sam = AuthUser("sam", "sam@example.com", "Sam", emailVerified = true)

    /** Calls to auth, devices and local data, in order. */
    private val events = mutableListOf<String>()
    private val auth = FakeAuth(events)
    private val users = FakeUsers()
    private val households = FakeHouseholds(users)
    private val devices = FakeDevices(events)

    private fun TestScope.started(): SessionManager {
        val manager = SessionManager(auth, users, households, FakeLocalData(events), devices, backgroundScope)
        DeviceSync(manager, devices, backgroundScope).start()
        advanceUntilIdle()
        return manager
    }

    private fun samInHousehold() {
        users.put(UserProfile("sam", "Sam", "h1", null))
        households.membership("h1", "sam").value = Membership.Active(Role.Member)
    }

    @Test
    fun `a start without a signed-in user deletes the push token`() = runTest(UnconfinedTestDispatcher()) {
        started()

        assertEquals(listOf("deleteToken"), events)
    }

    @Test
    fun `a start with a signed-in user registers the phone once`() = runTest(UnconfinedTestDispatcher()) {
        samInHousehold()
        auth.user.value = sam
        started()

        assertEquals(listOf("sync sam"), events)
    }

    @Test
    fun `signing in registers the phone and signing out deletes the token`() = runTest(UnconfinedTestDispatcher()) {
        val manager = started()
        samInHousehold()
        auth.user.value = sam
        advanceUntilIdle()

        manager.signOut()
        advanceUntilIdle()

        assertEquals(
            listOf("deleteToken", "sync sam", "unregister sam", "clear", "signOut", "deleteToken"),
            events,
        )
    }

    @Test
    fun `deleting the account deletes the token`() = runTest(UnconfinedTestDispatcher()) {
        samInHousehold()
        auth.user.value = sam
        val manager = started()

        manager.deleteAccount("password1")
        advanceUntilIdle()

        assertEquals(listOf("sync sam", "clear", "delete", "deleteToken"), events)
    }
}
