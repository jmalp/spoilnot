package com.parcha.spoilnot.core.firebase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportPolicyTest {
    @Test
    fun `only builds on real Firebase send crash reports, and only when the setting allows`() {
        assertTrue(CrashReportPolicy.collects(enabled = true, debug = false, emulatorHost = ""))
        assertFalse(CrashReportPolicy.collects(enabled = false, debug = false, emulatorHost = ""))
        assertFalse(CrashReportPolicy.collects(enabled = true, debug = true, emulatorHost = ""))
    }

    @Test
    fun `the benchmark build, release code on the emulators, sends none`() {
        assertFalse(CrashReportPolicy.collects(enabled = true, debug = false, emulatorHost = "10.0.2.2"))
    }
}
