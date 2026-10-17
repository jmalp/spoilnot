package com.parcha.spoilnot.core.crash

/** Crash reports (DESIGN 7.5): uncaught errors only, no personal data, with an off switch in Settings. */
interface CrashReporting {
    fun setEnabled(enabled: Boolean)
}
