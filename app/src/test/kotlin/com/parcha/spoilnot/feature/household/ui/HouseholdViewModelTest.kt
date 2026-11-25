package com.parcha.spoilnot.feature.household.ui

import com.parcha.spoilnot.feature.auth.domain.AuthUser
import com.parcha.spoilnot.feature.auth.domain.UserProfile
import com.parcha.spoilnot.feature.household.domain.FakeAuth
import com.parcha.spoilnot.feature.household.domain.FakeDevices
import com.parcha.spoilnot.feature.household.domain.FakeHouseholds
import com.parcha.spoilnot.feature.household.domain.FakeLocalData
import com.parcha.spoilnot.feature.household.domain.FakeUsers
import com.parcha.spoilnot.feature.household.domain.Household
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Member
import com.parcha.spoilnot.feature.household.domain.Membership
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.feature.household.domain.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Household settings' Remove member (DESIGN 5.1; after v1, S5). */
@OptIn(ExperimentalCoroutinesApi::class)
class HouseholdViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val events = mutableListOf<String>()
    private val auth = FakeAuth(events)
    private val users = FakeUsers()
    private val households = FakeHouseholds(users)
    private val sam = AuthUser("sam", "sam@example.com", "Sam", emailVerified = true)
    private val home = Household("h1", "Home", "sam", "ABCDE23456")
    private val alex = Member("alex", "Alex", Role.Member, null)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        users.put(UserProfile("sam", "Sam", "h1", null))
        households.membership("h1", "sam").value = Membership.Active(Role.Owner)
        households.household("h1").value = home
        households.members("h1").value = listOf(Member("sam", "Sam", Role.Owner, null), alex)
        auth.user.value = sam
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `removing a member also replaces the invite code they know`() = runTest(dispatcher) {
        val session =
            SessionManager(auth, users, households, FakeLocalData(events), FakeDevices(events), backgroundScope)
        val model = HouseholdViewModel(session, households)
        backgroundScope.launch { model.content.collect {} }
        model.content.filterNotNull().first { it.household != null && it.members.size == 2 }

        model.remove(alex)
        advanceUntilIdle()

        val (hid, removed, owner, newCode) = households.removals.single()
        assertEquals(listOf("h1", "alex", "sam"), listOf(hid, removed, owner))
        assertTrue(InviteCodes.isWellFormed(newCode))
        assertNotEquals(home.inviteCode, newCode)
        val shown = model.content.value!!
        assertEquals(newCode, shown.household?.inviteCode)
        assertEquals(listOf("sam"), shown.members.map { it.uid })
        assertEquals(HouseholdViewModel.Actions(), model.actions.value)
    }
}
