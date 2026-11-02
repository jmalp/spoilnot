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
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.parcha.spoilnot.EmulatorAdmin
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.MainActivity
import com.parcha.spoilnot.R
import com.parcha.spoilnot.TestPhone
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.notifications.FakePushIdentity
import com.parcha.spoilnot.feature.notifications.domain.DeviceSync
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * Sign out and Delete account through Settings in the real app (DESIGN 5.1, 10.2, 12.2 control 6). Both remove the
 * Settings screen as they start, and must still finish: checked on the emulators' server and in the phone's cache.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsAccountUiTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    /** Before the activity starts: emulator access granted (no permission prompt) and nobody signed in. */
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

    @Inject lateinit var firestore: FirestoreProvider

    @Inject lateinit var deviceSync: DeviceSync

    @Inject lateinit var push: FakePushIdentity

    private var owner: TestPhone? = null

    @Before
    fun registerDevicesAsTheAppDoes() {
        hilt.inject()
        // SpoilNotApp starts it in the app; Hilt's test application does not.
        deviceSync.start()
    }

    @After
    fun closeOwner() {
        owner?.close()
    }

    private fun str(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun button(label: Int) = compose.onNode(hasText(str(label)) and hasClickAction())

    private fun awaitTag(tag: String, timeoutMs: Long = 20_000): SemanticsNodeInteraction {
        compose.waitUntilAtLeastOneExists(hasTestTag(tag), timeoutMs)
        return compose.onNodeWithTag(tag)
    }

    private fun eventually(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > end) fail("$what: still not true after $timeoutMs ms")
            Thread.sleep(POLL_MS)
        }
    }

    private fun devicePath(uid: String) = "users/$uid/devices/${FakePushIdentity.INSTALL_ID}"

    /** Whether this phone's Firestore cache has the document; reading it from the cache alone fails otherwise. */
    private fun cached(path: String): Boolean = runBlocking {
        try {
            firestore.current.document(path).get(Source.CACHE).await().exists()
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.UNAVAILABLE, e.code)
            false
        }
    }

    private fun inHousehold(): Session.InHousehold = runBlocking {
        withTimeout(20_000) { sessionManager.session.first { it is Session.InHousehold } } as Session.InHousehold
    }

    private fun openSettings() {
        awaitTag("screen_home")
        compose.onNodeWithContentDescription(str(R.string.open_settings)).performClick()
        awaitTag("screen_settings")
    }

    @Test
    fun signOutRemovesThisPhonesDeviceAndTheCachedHousehold() {
        awaitTag("screen_welcome")
        // Nobody signed in at start: the phone keeps no push token.
        eventually("token deleted at start") { push.tokenDeletes.get() == 1 }

        runBlocking {
            sessionManager.signUp("Casey", uniqueEmail("casey"), PASSWORD)
            val gate = withTimeout(20_000) { sessionManager.session.first { it is Session.NoHousehold } }
            val uid = (gate as Session.NoHousehold).user.uid
            households.createHousehold(uid, "Casey", "Casey home", InviteCodes.generate())
        }
        val me = inHousehold()
        val uid = me.user.uid
        eventually("device registered") { EmulatorAdmin.documentExists(devicePath(uid)) }
        openSettings()
        eventually("household cached before sign-out") { cached("households/${me.householdId}") }

        button(R.string.action_sign_out).performScrollTo().performClick()
        awaitTag("screen_welcome")

        eventually("device document deleted on the server") { !EmulatorAdmin.documentExists(devicePath(uid)) }
        assertFalse("Household no longer in this phone's cache", cached("households/${me.householdId}"))
        assertFalse("Profile no longer in this phone's cache", cached("users/$uid"))
        eventually("token deleted after sign-out") { push.tokenDeletes.get() == 2 }
    }

    @Test
    fun deleteAccountRemovesProfileDevicesMembershipAndSignIn() {
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
        val uid = runBlocking {
            sessionManager.signUp("Bea", uniqueEmail("bea"), PASSWORD)
            val gate = withTimeout(20_000) { sessionManager.session.first { it is Session.NoHousehold } }
            val uid = (gate as Session.NoHousehold).user.uid
            households.requestToJoin(uid, "Bea", checkNotNull(households.findInviteCode(code)))
            val ana = checkNotNull(owner)
            val request = withTimeout(10_000) {
                ana.households.observeJoinRequests(householdId).first { list -> list.any { it.uid == uid } }
            }.single { it.uid == uid }
            ana.households.approve(householdId, request)
            uid
        }
        assertEquals(householdId, inHousehold().householdId)
        eventually("device registered") { EmulatorAdmin.documentExists(devicePath(uid)) }
        assertTrue(EmulatorAdmin.documentExists("users/$uid"))
        assertTrue(EmulatorAdmin.authUserExists(uid))

        openSettings()
        button(R.string.action_delete_account).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput(PASSWORD)
        compose.onNode(hasText(str(R.string.action_delete)) and hasClickAction() and hasAnyAncestor(isDialog()))
            .performClick()
        awaitTag("screen_welcome", timeoutMs = 30_000)

        eventually("profile deleted") { !EmulatorAdmin.documentExists("users/$uid") }
        assertEquals(emptyList<String>(), EmulatorAdmin.documentIds("users/$uid/devices"))
        assertFalse("Membership deleted", EmulatorAdmin.documentExists("households/$householdId/members/$uid"))
        assertFalse("Sign-in deleted", EmulatorAdmin.authUserExists(uid))
    }

    private companion object {
        const val POLL_MS = 250L
    }
}
