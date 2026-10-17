package com.parcha.spoilnot

import android.app.Application
import com.parcha.spoilnot.feature.grocery.domain.RestockRunner
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class SpoilNotApp : Application() {
    @Inject lateinit var restockRunner: RestockRunner

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // The restock engine runs on every phone while the user is in a household (DESIGN 9.6).
        restockRunner.start()
    }
}
