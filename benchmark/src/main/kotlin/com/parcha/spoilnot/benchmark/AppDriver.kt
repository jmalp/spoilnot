package com.parcha.spoilnot.benchmark

import android.os.Build
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** The dev benchmark build: release code that talks to the local Firebase emulators. */
const val TARGET_PACKAGE = "com.parcha.spoilnot.dev"

/**
 * The emulator-only account with 500 batches, created by `PantryScrollPerfTest` in the app's instrumented tests
 * (run it once with `-e perf true` before generating or benchmarking; docs/RUNBOOK.md).
 */
private const val PERF_EMAIL = "perf-500@example.com"
private const val PERF_PASSWORD = "correct-horse-battery"

private const val TIMEOUT_MS = 20_000L

/** Grants what the app would otherwise ask for, so no system dialog covers it. */
fun MacrobenchmarkScope.grantPermissions() {
    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= 37) add("android.permission.ACCESS_LOCAL_NETWORK")
        if (Build.VERSION.SDK_INT >= 33) add("android.permission.POST_NOTIFICATIONS")
    }
    permissions.forEach { device.executeShellCommand("pm grant $packageName $it") }
}

/**
 * Signs in to the perf household when the app shows the welcome screen (does nothing when already signed in), then
 * skips the app tour if it shows.
 */
fun MacrobenchmarkScope.signInIfNeeded() {
    device.wait(Until.hasObject(By.text(Pattern.compile("Pantry|Sign in"))), TIMEOUT_MS)
    val signIn = device.findObject(button("Sign in")) ?: return skipTourIfShown()
    signIn.click()
    type(field("Email"), PERF_EMAIL)
    type(field("Password"), PERF_PASSWORD)
    device.pressBack() // close the keyboard so the button is visible
    // The screen title also says "Sign in", so look for the clickable button.
    device.wait(Until.findObject(button("Sign in")), TIMEOUT_MS)?.click()
    check(device.wait(Until.hasObject(By.text("Pantry")), TIMEOUT_MS)) {
        "Sign-in failed: run PantryScrollPerfTest with -e perf true first, with the emulators running"
    }
    skipTourIfShown()
}

/** Skips the app tour (DESIGN 5.8) when it shows, so it never covers what is being measured. */
fun MacrobenchmarkScope.skipTourIfShown() {
    device.wait(Until.findObject(button("Skip tour")), 2_000L)?.click()
    device.waitForIdle()
}

/** A Compose button: the clickable node around the label. */
private fun button(label: String) = By.clickable(true).hasDescendant(By.text(label))

private fun MacrobenchmarkScope.field(label: String): UiObject2 =
    checkNotNull(device.wait(Until.findObject(By.text(label)), TIMEOUT_MS)) { "No $label field" }

private fun MacrobenchmarkScope.type(field: UiObject2, text: String) {
    field.click()
    device.executeShellCommand("input text $text")
}

/** Opens a bottom navigation tab by its label. */
fun MacrobenchmarkScope.openTab(label: String) {
    val tab = checkNotNull(device.wait(Until.findObject(By.text(label)), TIMEOUT_MS)) { "No $label tab" }
    tab.click()
    device.waitForIdle()
}

/**
 * Flings the current screen's list down and back up with screen swipes. Swipes keep no reference to the list, which
 * the app redraws as data arrives (a held UiObject2 goes stale).
 */
fun MacrobenchmarkScope.flingList(times: Int = 3) {
    val x = device.displayWidth / 2
    val low = device.displayHeight * 3 / 4
    val high = device.displayHeight / 4
    repeat(times) {
        device.swipe(x, low, x, high, FLING_STEPS)
        device.waitForIdle()
    }
    repeat(times) {
        device.swipe(x, high, x, low, FLING_STEPS)
        device.waitForIdle()
    }
}

/** Swipe steps take about 5 ms each, so 10 steps is a quick fling. */
private const val FLING_STEPS = 10

/**
 * Taps [target] until [opened] shows, at most three times, and says whether it did. A tap right after a fling only
 * stops the fling in Compose, so the first tap can do nothing.
 */
private fun MacrobenchmarkScope.tapUntil(target: BySelector, opened: BySelector): Boolean {
    repeat(3) {
        device.wait(Until.findObject(target), TIMEOUT_MS)?.click() ?: return false
        if (device.wait(Until.hasObject(opened), OPEN_TIMEOUT_MS)) return true
    }
    return false
}

/**
 * Presses Back while [shown] is on screen, at most twice (the keyboard, then the window), so a Back never leaves the
 * tab, or the app, by mistake.
 */
private fun MacrobenchmarkScope.closeWhileShown(shown: BySelector) {
    repeat(2) {
        if (!device.hasObject(shown)) return
        device.pressBack()
        device.waitForIdle()
    }
}

private const val OPEN_TIMEOUT_MS = 5_000L

/** Opens the Add item window from the Pantry tab and closes it again. */
fun MacrobenchmarkScope.openAndCloseAddItem() {
    if (tapUntil(By.text("Add item"), By.text("Name"))) closeWhileShown(By.text("Name"))
}

/** Opens the add sheet of the first slot on the Plan tab (DESIGN 5.9) and closes it again. */
fun MacrobenchmarkScope.openAndCloseAddMeal() {
    if (tapUntil(By.descStartsWith("Add to "), By.text("Who's cooking?"))) closeWhileShown(By.text("Who's cooking?"))
}

/** Opens Insights from Home's "Your kitchen this month" card and comes back. */
fun MacrobenchmarkScope.openAndCloseInsights() {
    device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN, Until.findObject(button("See insights")))
    if (!tapUntil(button("See insights"), By.text("Last 6 months"))) return
    flingList(times = 1)
    closeWhileShown(By.text("Last 6 months"))
}
