package com.parcha.spoilnot.perf

import android.util.Log
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.MainActivity
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.Location
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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DESIGN 13.1 budget: smooth scrolling with 500 batches. Seeds 500 batches (250 products, two each), flings the
 * Pantry list and logs Android's frame statistics. Opt in with `-e perf true`; the numbers are a debug build's, so
 * a release build does better.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PantryScrollPerfTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var session: SessionManager

    @Inject lateinit var households: HouseholdRepository

    @Inject lateinit var pantry: PantryService

    @Inject lateinit var pantryRepository: PantryRepository

    @Inject lateinit var writer: HouseholdWriter

    private var scenario: ActivityScenario<MainActivity>? = null
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun seed500Batches() = runBlocking {
        assumeTrue(
            "perf runs only with -e perf true",
            InstrumentationRegistry.getArguments().getString("perf") == "true",
        )
        EmulatorSupport.requireEmulatorAndNetworkAccess()
        hilt.inject()
        FirebaseAuth.getInstance().signOut()
        // A fixed emulator account, so the release-like benchmark build can sign in to the same data.
        if (runCatching { session.signIn(PERF_EMAIL, PASSWORD) }.isSuccess) {
            val signedIn = withTimeout(15_000) {
                session.session.first { it is Session.InHousehold || it is Session.NoHousehold }
            }
            // The account outlives a cleared Firestore emulator (the rules tests clear it); seed again then.
            if (signedIn is Session.InHousehold) return@runBlocking
        } else {
            session.signUp("Pat", PERF_EMAIL, PASSWORD)
            withTimeout(15_000) { session.session.first { it is Session.NoHousehold } }
        }
        val uid = checkNotNull(FirebaseAuth.getInstance().currentUser).uid
        val hid = households.createHousehold(uid, "Pat", "Perf home", InviteCodes.generate())
        withTimeout(15_000) { session.session.first { it is Session.InHousehold } }
        var data = withTimeout(10_000) { pantryRepository.observe(hid).first() }
        val changes = mutableListOf<HouseholdChange>()
        val today = LocalDate.now()
        repeat(500) { i ->
            val draft = StockDraft(
                "Item ${i / 2}", 1.0 + i % 3, QuantityUnit.Pieces, today.plusDays((i % 40).toLong() - 5),
                DateType.UseBy, Location.entries[i % 4], Category.entries[i % 9], null, null, false, null,
            )
            val plan = pantry.planAdd(draft, data)
            changes += plan.changes
            data = plan.after
        }
        writer.apply(hid, Actor(uid, "Pat"), changes)
        withTimeout(60_000) { pantryRepository.observe(hid).first { !it.fromCache && it.batches.size == 500 } }
        Unit
    }

    @After
    fun tearDown() {
        scenario?.close()
        FirebaseAuth.getInstance().signOut()
    }

    companion object {
        /** Emulator-only account holding the 500-batch household. */
        const val PERF_EMAIL = "perf-500@example.com"
    }

    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).let { fd ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }

    @Test
    fun pantryScrollsSmoothlyWith500Batches() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val context = instrumentation.targetContext
        compose.waitUntilAtLeastOneExists(hasText(context.getString(R.string.tab_pantry)) and isSelectable(), 20_000)
        compose.onNode(hasText(context.getString(R.string.tab_pantry)) and isSelectable()).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("pantry_list"), 20_000)
        compose.waitForIdle()

        val pkg = context.packageName
        shell("dumpsys gfxinfo $pkg reset")
        repeat(6) {
            repeat(4) { compose.onNodeWithTag("pantry_list").performTouchInput { swipeUp(durationMillis = 150) } }
            repeat(4) { compose.onNodeWithTag("pantry_list").performTouchInput { swipeDown(durationMillis = 150) } }
        }
        compose.waitForIdle()
        val stats = shell("dumpsys gfxinfo $pkg")
        val lines = stats.lines().filter {
            it.contains("Total frames rendered") || it.contains("Janky frames") || it.contains("percentile")
        }.take(8)
        lines.forEach { Log.i("PantryScrollPerf", it.trim()) }
    }
}
