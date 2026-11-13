package com.parcha.spoilnot.feature.notifications

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.MainActivity
import com.parcha.spoilnot.R
import com.parcha.spoilnot.TestPhone
import com.parcha.spoilnot.core.prefs.AppPreferences
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.notifications.domain.YoureSet
import com.parcha.spoilnot.feature.notifications.domain.YoureSetSender
import com.parcha.spoilnot.feature.notifications.ui.formatHour
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import com.parcha.spoilnot.feature.pantry.domain.PantryService
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * Reminders people can count on (after v1, DESIGN 5.4; review UX-02, UX-03): a member who joins a household with dated
 * food is asked on Home, without adding anything; Not now leaves Home's "Reminders are off on this phone · Turn on";
 * a new household is asked at its third dated item; "You're set" names the time and tomorrow's items.
 *
 * Whether the phone allows notifications comes from [FakeNotificationAccess] (blocked here): the emulator's own
 * permission can't be taken back during a test.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RemindersUiTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    /** Before the activity starts: nobody signed in, and a phone that has not explained reminders yet. */
    @get:Rule(order = 1)
    val newPhone = object : ExternalResource() {
        override fun before() {
            EmulatorSupport.requireEmulatorAndNetworkAccess()
            FirebaseAuth.getInstance().signOut()
            runBlocking { preferences().setNotificationExplainerShown(false) }
        }
    }

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var sessionManager: SessionManager

    @Inject lateinit var households: HouseholdRepository

    @Inject lateinit var pantry: PantryService

    @Inject lateinit var pantryRepository: PantryRepository

    @Inject lateinit var access: FakeNotificationAccess

    @Inject lateinit var youreSet: YoureSetSender

    private var owner: TestPhone? = null

    private val today: LocalDate = LocalDate.now()

    @Before
    fun blockNotifications() {
        hilt.inject()
        access.allowed = false
        access.youreSet.clear()
    }

    @After
    fun tearDown() {
        access.allowed = true
        owner?.close()
        runBlocking { preferences().markNotificationExplainerShown() }
    }

    private fun preferences() =
        AppPreferences(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)

    private fun str(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun awaitTag(tag: String, timeoutMs: Long = 20_000): SemanticsNodeInteraction {
        compose.waitUntilAtLeastOneExists(hasTestTag(tag), timeoutMs)
        return compose.onNodeWithTag(tag)
    }

    private fun draft(name: String, inDays: Long?) = StockDraft(
        name, 1.0, QuantityUnit.Pieces, inDays?.let { today.plusDays(it) }, DateType.UseBy, Location.Fridge,
        Category.Other, null, null, false, null,
    )

    private suspend fun PantryRepository.loaded(hid: String, batches: Int): PantryData =
        withTimeout(20_000) { observe(hid).first { !it.fromCache && it.batches.size == batches } }

    @Test
    fun aMemberWhoJoinsIsAskedOnHome_andNotNowLeavesTheLine() {
        awaitTag("screen_welcome")
        val code = InviteCodes.generate()
        // Ana's household already has dated food: chicken tomorrow, spinach the day after.
        val householdId = runBlocking {
            val ana = TestPhone("ana").also { owner = it }
            ana.sessionManager.signUp("Ana", uniqueEmail("ana"), PASSWORD)
            ana.awaitSession { it is Session.NoHousehold }
            val hid = ana.households.createHousehold(ana.uid, "Ana", "Ana home", code)
            ana.awaitSession { it is Session.InHousehold }
            ana.pantry.add(hid, ana.actor, draft("Chicken", 1), ana.pantryRepository.loaded(hid, 0))
            ana.pantry.add(hid, ana.actor, draft("Spinach", 2), ana.pantryRepository.loaded(hid, 1))
            hid
        }
        runBlocking {
            sessionManager.signUp("Bea", uniqueEmail("bea"), PASSWORD)
            val gate = withTimeout(20_000) { sessionManager.session.first { it is Session.NoHousehold } }
            val uid = (gate as Session.NoHousehold).user.uid
            households.requestToJoin(uid, "Bea", checkNotNull(households.findInviteCode(code)))
            val ana = checkNotNull(owner)
            val request = withTimeout(10_000) {
                ana.households.observeJoinRequests(householdId).first { list -> list.any { it.uid == uid } }
            }.single { it.uid == uid }
            ana.households.approve(householdId, request)
        }

        // Bea never opens Pantry or adds anything: Home asks, naming the reminder time.
        awaitTag("screen_home")
        awaitTag("notification_explainer")
        compose.onNode(hasText(str(R.string.notif_explainer_body_time, formatHour(9))) and hasAnyAncestor(isDialog()))
            .assertExists()
        compose.onNode(hasText(str(R.string.notif_explainer_later)) and hasAnyAncestor(isDialog())).performClick()

        // Not now: Home says so where the food is, with Turn on, and the phone remembers the answer.
        awaitTag("home_reminders_off")
        compose.onNodeWithTag("home_reminders_turn_on").assertExists()
        compose.waitUntilDoesNotExist(hasTestTag("notification_explainer"), 5_000)
        assertTrue(runBlocking { withTimeout(5_000) { preferences().notificationExplainerShown.first { it } } })

        // Once the phone allows them, "You're set" names the time and tomorrow's items, soonest first.
        access.allowed = true
        assertTrue(runBlocking { youreSet.afterTurnOn() })
        assertEquals(YoureSet(9, listOf("Chicken", "Spinach"), 0), access.youreSet.single())
    }

    @Test
    fun aNewHouseholdIsAskedAtItsThirdDatedItem() {
        awaitTag("screen_welcome")
        val hid = runBlocking {
            sessionManager.signUp("Cal", uniqueEmail("cal"), PASSWORD)
            val gate = withTimeout(20_000) { sessionManager.session.first { it is Session.NoHousehold } }
            val uid = (gate as Session.NoHousehold).user.uid
            households.createHousehold(uid, "Cal", "Cal home", InviteCodes.generate()).also {
                withTimeout(20_000) { sessionManager.session.first { it is Session.InHousehold } }
            }
        }
        awaitTag("screen_home")
        // The shell has read the empty pantry from the server: the household opened with no dated food.
        runBlocking { pantryRepository.loaded(hid, 0) }
        Thread.sleep(OPEN_SETTLE_MS)
        val actor = runBlocking {
            val me = sessionManager.session.value as Session.InHousehold
            Actor(me.user.uid, me.displayName)
        }
        // The owner's first items, and one with no date: not yet.
        runBlocking {
            pantry.add(hid, actor, draft("Milk", 3), pantryRepository.loaded(hid, 0))
            pantry.add(hid, actor, draft("Rice", null), pantryRepository.loaded(hid, 1))
            pantry.add(hid, actor, draft("Eggs", 5), pantryRepository.loaded(hid, 2))
            pantryRepository.loaded(hid, 3)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("notification_explainer").assertDoesNotExist()

        runBlocking { pantry.add(hid, actor, draft("Yogurt", 4), pantryRepository.loaded(hid, 3)) }
        awaitTag("notification_explainer")
    }

    private companion object {
        /** Lets the shell's listener take the empty pantry before the first item lands. */
        const val OPEN_SETTLE_MS = 2_000L
    }
}
