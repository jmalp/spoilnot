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
 * Collection starts off (manifest) and is switched on at app start from the setting. Debug builds never send: they
 * use the emulators' demo project.
 */
class FirebaseCrashReporting @Inject constructor() : CrashReporting {
    override fun setEnabled(enabled: Boolean) {
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = enabled && !BuildConfig.DEBUG
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface CrashReportingModule {
    @Binds
    fun crashReporting(impl: FirebaseCrashReporting): CrashReporting
}
