package com.parcha.spoilnot.feature.recipes

import android.content.Intent
import android.util.Log
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.MainActivity
import com.parcha.spoilnot.TimingTest
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M5's acceptance time (DESIGN 14): a link shared to the app shows its preview in under 5 s, from the share to the
 * preview, activity start included. `RecipeUiTest` checks the same flow in every run but gives the whole share 60 s,
 * since a busy emulator can take that long to start an activity; this test holds the 5 s and so runs only when asked
 * (`timing=true`, RUNBOOK "Opt-in instrumented tests"). Pages come from FakeRecipeImportModule, not the network.
 */
@OptIn(ExperimentalTestApi::class)
@TimingTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SharePreviewTimingTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var session: SessionManager

    @Inject lateinit var households: HouseholdRepository

    private var scenario: ActivityScenario<MainActivity>? = null

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun signedInWithAHousehold() = runBlocking {
        EmulatorSupport.requireEmulatorAndNetworkAccess()
        hilt.inject()
        FirebaseAuth.getInstance().signOut()
        session.signUp("Robin", uniqueEmail("robin"), PASSWORD)
        withTimeout(15_000) { session.session.first { it is Session.NoHousehold } }
        val uid = checkNotNull(FirebaseAuth.getInstance().currentUser).uid
        households.createHousehold(uid, "Robin", "Share timing", InviteCodes.generate())
        withTimeout(15_000) { session.session.first { it is Session.InHousehold } }
        Unit
    }

    @After
    fun tearDown() {
        scenario?.close()
        FirebaseAuth.getInstance().signOut()
    }

    private fun share(text: String): Intent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)

    @Test
    fun aSharedLinkShowsItsPreviewInUnder5Seconds() {
        // Each share starts the activity afresh, as a share from Chrome does; the median of three is held to 5 s.
        val times = (1..SHARES).map {
            scenario?.close()
            val start = System.nanoTime()
            scenario = ActivityScenario.launch(share("Try these: ${FakeRecipeImportModule.PANCAKES_URL}"))
            compose.waitUntilAtLeastOneExists(hasTestTag("preview_title") and hasText("Test pancakes"), 60_000)
            (System.nanoTime() - start) / 1_000_000
        }
        Log.i("SharePreviewTimingTest", "share to preview: $times ms")
        val median = times.sorted()[SHARES / 2]
        assertTrue("share to preview, median of $times ms", median < BUDGET_MS)
    }

    private companion object {
        const val SHARES = 3
        const val BUDGET_MS = 5_000L
    }
}
