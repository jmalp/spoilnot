package com.parcha.spoilnot.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frames on every tab beyond Pantry's first scroll (after v1, 2026-11-17): review 08's scenarios, kept so later changes
 * can be compared with it (DESIGN 13.1, RUNBOOK "Benchmark setup"). All with the baseline profile, as the shipped app
 * runs. The data is the perf household `PantryScrollPerfTest` seeds: 500 batches, 150 grocery items (30 in the cart),
 * 40 recipes without photos, three meals a day for three weeks and six months of counts.
 *
 * Each iteration starts the app fresh, opens the tab and waits a moment for its data; only the measure block counts.
 * "First" measures a list's first scroll after the start, "steady" flings once before measuring. Run one test at a
 * time (`-Pandroid.testInstrumentationRunnerArguments.class=…JankBenchmark#name`); all of them take about 25 minutes.
 */
@RunWith(AndroidJUnit4::class)
class JankBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun pantryScrollSteady() = scroll("Pantry", warmUp = true)

    @Test
    fun groceriesScrollFirst() = scroll("Groceries", warmUp = false)

    @Test
    fun groceriesScrollSteady() = scroll("Groceries", warmUp = true)

    @Test
    fun recipesScrollFirst() = scroll("Recipes", warmUp = false)

    @Test
    fun recipesScrollSteady() = scroll("Recipes", warmUp = true)

    @Test
    fun planScrollFirst() = scroll("Plan", warmUp = false)

    @Test
    fun homeScrollFirst() = scroll("Home", warmUp = false, times = 2)

    /** Your kitchen from Home's card: the charts grow, one fling, back (review PERF-03). */
    @Test
    fun insightsOpenAndScroll() = measure("Home") {
        openInsights()
        flingList(times = 1)
        closeInsights()
    }

    /** Every tab twice: the first round composes each tab, the second shows them again (review PERF-09). */
    @Test
    fun tabSwitches() = measure("Home") {
        repeat(2) {
            for (tab in TABS) {
                openTab(tab)
                SystemClock.sleep(400)
            }
        }
    }

    /** Add item: the sheet opens with the keyboard on Name, is dragged a little and let go, then closes. */
    @Test
    fun addItemSheetAndKeyboard() = measure("Pantry") {
        val name = By.text("Name")
        check(tapUntil(screen("fab_add_item"), name)) { "Pantry's Add item didn't open" }
        SystemClock.sleep(800) // Name takes the focus and the keyboard opens
        device.pressBack() // the keyboard
        device.waitForIdle()
        dragSheet(name, by = device.displayHeight / 5)
        close(name, screen("screen_pantry"), "Add item")
    }

    /** Plan's add sheet: open, a short drag let go, close (review PERF-09). */
    @Test
    fun planSheet() = measure("Plan") {
        val opened = By.text("Who's cooking?")
        check(tapUntil(By.descStartsWith("Add to "), opened)) { "Plan's add sheet didn't open" }
        dragSheet(opened, by = device.displayHeight / 6)
        close(opened, screen("screen_plan"), "Plan's add sheet")
    }

    /** Typing in Pantry's search: every key filters the 500 batches again. */
    @Test
    fun pantrySearchTyping() = typing("Pantry", "pantry_search")

    /** Typing in Groceries' quick add: every key looks for suggestions in 250 products and 150 items (review PERF-04). */
    @Test
    fun groceriesQuickAddTyping() = typing("Groceries", "grocery_quick_add")

    private fun typing(tab: String, field: String) = measure(tab, prepare = {
        checkNotNull(device.wait(Until.findObject(screen(field)), OPEN_TIMEOUT_MS)) { "No $field" }.click()
        SystemClock.sleep(800) // the keyboard opens
    }) {
        for (c in "item 12") {
            device.executeShellCommand(if (c == ' ') "input keyevent 62" else "input text $c")
            SystemClock.sleep(150)
        }
        SystemClock.sleep(500)
    }

    /** A slow drag down from just above [inSheet] by [by] pixels, let go, so the sheet settles back. */
    private fun MacrobenchmarkScope.dragSheet(inSheet: BySelector, by: Int) {
        val x = device.displayWidth / 2
        val top = device.findObject(inSheet)?.visibleBounds?.top ?: (device.displayHeight / 2)
        device.drag(x, top - 100, x, top - 100 + by, 40)
        SystemClock.sleep(600)
    }

    private fun scroll(tab: String, warmUp: Boolean, times: Int = 3) = measure(tab, prepare = {
        if (warmUp) {
            flingList(times = 1)
            SystemClock.sleep(500)
        }
    }) {
        flingList(times)
    }

    private fun measure(
        tab: String,
        prepare: MacrobenchmarkScope.() -> Unit = {},
        block: MacrobenchmarkScope.() -> Unit,
    ) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        iterations = 5,
        setupBlock = {
            grantPermissions()
            killProcess()
            startActivityAndWait()
            signInIfNeeded()
            openTab(tab)
            SystemClock.sleep(1_500)
            prepare()
        },
        measureBlock = block,
    )

    private companion object {
        val TABS = listOf("Pantry", "Plan", "Groceries", "Recipes", "Home")
    }
}
