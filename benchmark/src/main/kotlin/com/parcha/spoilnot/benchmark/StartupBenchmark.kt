package com.parcha.spoilnot.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start to Home's cards, without precompiled code and with the baseline profile (DESIGN 13.1: under 2.5 s).
 * `timeToInitialDisplayMs` is the first frame (Home's skeleton); `timeToFullDisplayMs` is when Home reports itself
 * drawn with its cards, which is what people wait for and what the budget is measured on (after v1 review PERF-05).
 * The setup signs in first, so every iteration starts signed in, not only the ones after the first.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = {
            grantPermissions()
            pressHome()
            startActivityAndWait()
            signInIfNeeded()
            pressHome()
        },
    ) {
        startActivityAndWait()
        awaitHomeCards()
    }
}
