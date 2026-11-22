package com.parcha.spoilnot.feature.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.MainActivity
import com.parcha.spoilnot.R
import com.parcha.spoilnot.TestPhone
import com.parcha.spoilnot.closeKeyboard
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * Clear household data from Settings in the real app (QA flow 2.24, DESIGN 5.1; after v1, review CODE-08): only the
 * owner sees it; it asks "are you sure", then the password; Cancel and a wrong password clear nothing; the right one
 * empties the pantry on a member's phone too and keeps the household.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ClearHouseholdDataUiTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val signedOut = object : ExternalResource() {
        override fun before() {
            EmulatorSupport.requireEmulatorAndNetworkAccess()
            FirebaseAuth.getInstance().signOut()
        }
    }

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var sessionManager: SessionManager

    @Inject lateinit var households: HouseholdRepository

    private var other: TestPhone? = null

    @Before
    fun inject() = hilt.inject()

    @After
    fun closeOther() {
        other?.close()
    }

    private fun str(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun awaitTag(tag: String, timeoutMs: Long = 20_000): SemanticsNodeInteraction {
        compose.waitUntilAtLeastOneExists(hasTestTag(tag), timeoutMs)
        return compose.onNodeWithTag(tag)
    }

    private fun openSettings() {
        awaitTag("screen_home")
        compose.onNodeWithContentDescription(str(R.string.open_settings)).performClick()
        awaitTag("screen_settings")
    }

    /** A second phone that joins [householdId], approved by [approve]. */
    private suspend fun join(name: String, householdId: String, code: String, approve: suspend (String) -> Unit) =
        TestPhone(name).also { phone ->
            other = phone
            phone.sessionManager.signUp(name, uniqueEmail(name), PASSWORD)
            phone.awaitSession { it is Session.NoHousehold }
            phone.households.requestToJoin(phone.uid, name, checkNotNull(phone.households.findInviteCode(code)))
            approve(phone.uid)
            phone.awaitSession { it is Session.InHousehold && it.householdId == householdId }
        }

    private suspend fun TestPhone.pantry(householdId: String, predicate: (PantryData) -> Boolean): PantryData =
        withTimeout(20_000) { pantryRepository.observe(householdId).first { !it.fromCache && predicate(it) } }

    @Test
    fun theOwnerClearsTheDataAfterTheQuestionAndTheirPassword() {
        awaitTag("screen_welcome")
        val code = InviteCodes.generate()
        val householdId = runBlocking {
            sessionManager.signUp("Owen", uniqueEmail("owen"), PASSWORD)
            val gate = withTimeout(20_000) { sessionManager.session.first { it is Session.NoHousehold } }
            households.createHousehold((gate as Session.NoHousehold).user.uid, "Owen", "Owen home", code)
        }
        val member = runBlocking {
            join("Mia", householdId, code) { uid ->
                val request = withTimeout(10_000) {
                    households.observeJoinRequests(householdId).first { list -> list.any { it.uid == uid } }
                }.single { it.uid == uid }
                households.approve(householdId, request)
            }.also { mia ->
                val draft = StockDraft(
                    "Milk", 1.0, QuantityUnit.Pieces, LocalDate.now().plusDays(3), DateType.UseBy, Location.Fridge,
                    Category.Dairy, null, null, false, null,
                )
                mia.pantry.add(householdId, mia.actor, draft, mia.pantry(householdId) { true })
                mia.pantry(householdId) { d -> d.batches.size == 1 && d.batches.none { it.pendingSync } }
            }
        }
        openSettings()
        val clear = awaitTag("button_clear_household")

        // "Are you sure", answered Cancel: nothing happens.
        clear.performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText(str(R.string.clear_household_title)), 10_000)
        compose.onNode(hasText(str(R.string.action_cancel)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntilDoesNotExist(isDialog(), 5_000)

        // A wrong password says so and clears nothing.
        clear.performScrollTo().performClick()
        awaitTag("clear_household_yes").performClick()
        val password = hasSetTextAction() and hasAnyAncestor(isDialog())
        compose.waitUntilAtLeastOneExists(password, 10_000)
        compose.onNode(password).performTextInput("wrong-password")
        compose.closeKeyboard(hasTestTag("clear_household_confirm"))
        awaitTag("clear_household_confirm").performClick()
        compose.waitUntilAtLeastOneExists(hasText(str(R.string.error_invalid_credentials)), 20_000)
        assertEquals(1, runBlocking { member.pantry(householdId) { true } }.batches.size)

        // The right one clears it for everyone; the household and its members stay.
        compose.onNode(password).performTextClearance()
        compose.onNode(password).performTextInput(PASSWORD)
        compose.closeKeyboard(hasTestTag("clear_household_confirm"))
        awaitTag("clear_household_confirm").performClick()
        compose.waitUntilAtLeastOneExists(hasText(str(R.string.clear_household_done)), 30_000)
        compose.onNode(isDialog()).assertDoesNotExist()
        runBlocking { member.pantry(householdId) { it.batches.isEmpty() } }
        assertEquals(householdId, (member.sessionManager.session.value as Session.InHousehold).householdId)
        awaitTag("screen_settings")
    }

    @Test
    fun aMemberIsNotOfferedClearHouseholdData() {
        awaitTag("screen_welcome")
        val code = InviteCodes.generate()
        runBlocking {
            val owner = TestPhone("ana").also { other = it }
            owner.sessionManager.signUp("Ana", uniqueEmail("ana"), PASSWORD)
            owner.awaitSession { it is Session.NoHousehold }
            val householdId = owner.households.createHousehold(owner.uid, "Ana", "Ana home", code)
            owner.awaitSession { it is Session.InHousehold }
            sessionManager.signUp("Ben", uniqueEmail("ben"), PASSWORD)
            val gate = withTimeout(20_000) { sessionManager.session.first { it is Session.NoHousehold } }
            val uid = (gate as Session.NoHousehold).user.uid
            households.requestToJoin(uid, "Ben", checkNotNull(households.findInviteCode(code)))
            val request = withTimeout(10_000) {
                owner.households.observeJoinRequests(householdId).first { list -> list.any { it.uid == uid } }
            }.single { it.uid == uid }
            owner.households.approve(householdId, request)
            withTimeout(20_000) { sessionManager.session.first { it is Session.InHousehold } }
        }
        openSettings()
        // Once the household has loaded (its name on the Household row), the danger buttons end with Delete account.
        compose.waitUntilAtLeastOneExists(hasText("Ana home", substring = true), 20_000)
        compose.onNode(hasText(str(R.string.action_delete_account)) and hasClickAction()).performScrollTo()
        compose.onNodeWithTag("button_clear_household").assertDoesNotExist()
    }
}
