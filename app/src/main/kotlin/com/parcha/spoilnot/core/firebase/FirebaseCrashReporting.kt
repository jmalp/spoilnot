package com.parcha.spoilnot.core.firebase

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.parcha.spoilnot.BuildConfig
import com.parcha.spoilnot.core.crash.CrashReporting
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Collection starts off (manifest) and is switched on at app start from the setting, only in builds that talk to real
 * Firebase ([CrashReportPolicy]).
 */
class FirebaseCrashReporting @Inject constructor() : CrashReporting {
    override fun setEnabled(enabled: Boolean) {
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled =
            CrashReportPolicy.collects(enabled, BuildConfig.DEBUG, BuildConfig.FIREBASE_EMULATOR_HOST)
    }
}

/**
 * Debug builds and builds wired to the emulators (dev debug and dev benchmark) never send crash reports: the emulators'
 * demo project refuses every upload, and the retries it schedules restarted the app right after it was closed, which
 * made cold-start benchmarks fail ("must not be running prior to cold start").
 */
internal object CrashReportPolicy {
    fun collects(enabled: Boolean, debug: Boolean, emulatorHost: String): Boolean =
        enabled && !debug && emulatorHost.isBlank()
}

@Module
@InstallIn(SingletonComponent::class)
interface CrashReportingModule {
    @Binds
    fun crashReporting(impl: FirebaseCrashReporting): CrashReporting
}
