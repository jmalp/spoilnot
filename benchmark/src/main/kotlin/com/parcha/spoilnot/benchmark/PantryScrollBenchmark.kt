package com.parcha.spoilnot.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The first scroll through 500 batches after a fresh start, without and with the baseline profile. No startupMode:
 * with one, Macrobenchmark kills the app after the setup block, so the flings would land on the home screen. The setup
 * block restarts the app itself instead.
 */
@RunWith(AndroidJUnit4::class)
class PantryScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun firstScrollWithoutProfile() = scroll(CompilationMode.None())

    @Test
    fun firstScrollWithBaselineProfile() = scroll(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun scroll(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        iterations = 5,
        setupBlock = {
            grantPermissions()
            killProcess()
            startActivityAndWait()
            signInIfNeeded()
            openTab("Pantry")
        },
    ) {
        flingList()
    }
}
