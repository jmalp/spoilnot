package com.parcha.spoilnot

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [HiltTestRunner] leaves out each opt-in test whose argument the run didn't pass, so a default run reports no
 * skipped tests as failures (after v1 review BASE-02).
 */
@RunWith(AndroidJUnit4::class)
class OptInTestsTest {
    @Test
    fun runnerLeavesOutTheOptInTestsThisRunDidNotAskFor() {
        val args = InstrumentationRegistry.getArguments()
        val excluded = args.getString("notAnnotation").orEmpty().split(',').toSet()
        OptInTests.byArgument.forEach { (argument, annotation) ->
            assertEquals("$annotation left out", args.getString(argument) != "true", annotation in excluded)
        }
    }
}
