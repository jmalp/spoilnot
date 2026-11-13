package com.parcha.spoilnot.feature.household

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
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.feature.auth.domain.Units
import com.parcha.spoilnot.feature.auth.domain.UserRepository
import com.parcha.spoilnot.feature.household.domain.EmailMask
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * Joining and signing out in the real app (after v1, DESIGN 5.1): the waiting screen says the owner gets no
 * notification and the owner sees who asked; signing out with changes that haven't synced asks first.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class JoinAndSignOutUiTest {
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

    @Inject lateinit var users: UserRepository

    @Inject lateinit var firestore: FirestoreProvider

    private var owner: TestPhone? = null

    @Before
    fun inject() = hilt.inject()

    @After
    fun closeOwner() {
        owner?.close()
        // A test that failed half-way may leave this phone's Firestore offline.
        runBlocking { runCatching { firestore.current.enableNetwork().await() } }
    }

    private fun str(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun button(label: Int) = compose.onNode(hasText(str(label)) and hasClickAction())

    private fun awaitTag(tag: String, timeoutMs: Long = 20_000): SemanticsNodeInteraction {
        compose.waitUntilAtLeastOneExists(hasTestTag(tag), timeoutMs)
        return compose.onNodeWithTag(tag)
    }

    @Test
    fun waitingScreenSaysTheOwnerGetsNoNotification_andTheOwnerSeesWhoAsked() {
        awaitTag("screen_welcome")
        val code = InviteCodes.generate()
        val householdId = runBlocking {
            val ana = TestPhone("ana").also { owner = it }
            ana.sessionManager.signUp("Ana", uniqueEmail("ana"), PASSWORD)
            ana.awaitSession { it is Session.NoHousehold }
            ana.households.createHousehold(ana.uid, "Ana", "Ana home", code).also {
                ana.awaitSession { it is Session.InHousehold }
            }
        }
        val email = uniqueEmail("cole")
        runBlocking {
            sessionManager.signUp("Cole", email, PASSWORD)
            withTimeout(20_000) { sessionManager.session.first { it is Session.NoHousehold } }
        }

        awaitTag("card_join")
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("card_join"))).performTextInput(code)
        button(R.string.action_continue).performScrollTo().performClick()
        // The confirm dialog waits on a read of the invite; the default 1 s timed out on a busy emulator.
        compose.waitUntilAtLeastOneExists(
            hasText(str(R.string.action_send_request)) and hasAnyAncestor(isDialog()),
            10_000,
        )
        compose.onNode(hasText(str(R.string.action_send_request)) and hasAnyAncestor(isDialog())).performClick()

        awaitTag("card_join_status")
        compose.waitUntilAtLeastOneExists(hasText(str(R.string.waiting_body, "Ana home")), 10_000)

        val request = runBlocking {
            val ana = checkNotNull(owner)
            withTimeout(10_000) {
                ana.households.observeJoinRequests(householdId).first { it.isNotEmpty() }
            }.single()
        }
        assertEquals(EmailMask.mask(email), request.emailMasked)
    }

    @Test
    fun signOutWithUnsyncedChangesAsksFirst() {
        awaitTag("screen_welcome")
        runBlocking {
            sessionManager.signUp("Dee", uniqueEmail("dee"), PASSWORD)
            val gate = withTimeout(20_000) { sessionManager.session.first { it is Session.NoHousehold } }
            val uid = (gate as Session.NoHousehold).user.uid
            households.createHousehold(uid, "Dee", "Dee home", InviteCodes.generate())
            withTimeout(20_000) { sessionManager.session.first { it is Session.InHousehold } }
        }
        awaitTag("screen_home")
        compose.onNodeWithContentDescription(str(R.string.open_settings)).performClick()
        awaitTag("screen_settings")

        // A change made offline: it waits in this phone's queue.
        runBlocking {
            firestore.current.disableNetwork().await()
            val me = sessionManager.session.value as Session.InHousehold
            users.setUnits(me.user.uid, Units.Metric)
        }

        button(R.string.action_sign_out).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText(str(R.string.sign_out_unsynced_body)), 15_000)
        compose.onNode(hasText(str(R.string.action_cancel)) and hasAnyAncestor(isDialog())).performClick()
        awaitTag("screen_settings")
        assertEquals(true, sessionManager.session.value is Session.InHousehold)

        button(R.string.action_sign_out).performScrollTo().performClick()
        awaitTag("sign_out_anyway", timeoutMs = 15_000).performClick()
        awaitTag("screen_welcome", timeoutMs = 30_000)
    }
}
