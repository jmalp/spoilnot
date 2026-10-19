package com.parcha.spoilnot.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which code the app runs at start and on its busiest screens, so the release build ships it precompiled
 * (DESIGN 13.1). `./scripts/baseline-profile.sh` runs it and copies the result into the app. It repeats the journey
 * until three runs record the same code (at most 10, about 40 s each).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, maxIterations = 10) {
        grantPermissions()
        pressHome()
        startActivityAndWait()
        signInIfNeeded()
        openTab("Home")
        flingList(times = 1)
        openTab("Pantry")
        flingList()
        openAndCloseAddItem()
        openTab("Groceries")
        flingList(times = 1)
        openTab("Recipes")
        flingList(times = 1)
        openTab("Home")
    }
}
