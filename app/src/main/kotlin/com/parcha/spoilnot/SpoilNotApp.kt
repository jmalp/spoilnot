package com.parcha.spoilnot

import android.app.Application
import com.parcha.spoilnot.feature.grocery.domain.RestockRunner
import com.parcha.spoilnot.feature.notifications.domain.DeviceSync
import com.parcha.spoilnot.feature.notifications.ui.Notifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class SpoilNotApp : Application() {
    @Inject lateinit var restockRunner: RestockRunner

    @Inject lateinit var deviceSync: DeviceSync

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // The restock engine runs on every phone while the user is in a household (DESIGN 9.6).
        restockRunner.start()
        Notifications.createChannel(this)
        deviceSync.start()
    }
}
