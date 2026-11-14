package com.parcha.spoilnot

/**
 * Tests that run only when asked, kept out of the default `connectedDevDebugAndroidTest` run by [HiltTestRunner]
 * rather than skipped inside it, because the test report counts a skipped test as a failure (DESIGN 13.1, after v1
 * review BASE-02).
 */
object OptInTests {
    /** Each opt-in annotation and the runner argument that runs it (`-e <argument> true`). */
    val byArgument = mapOf(
        "live" to LiveTest::class.java.name,
        "perf" to PerfTest::class.java.name,
    )
}

/** Needs the internet (real recipe sites); runs with `-e live true` (docs/import-test-sites.md). */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class LiveTest

/** Seeds the 500-batch household for the benchmarks; runs with `-e perf true` (scripts/baseline-profile.sh). */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PerfTest
