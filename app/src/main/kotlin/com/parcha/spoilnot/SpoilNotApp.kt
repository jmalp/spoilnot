package com.parcha.spoilnot

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.parcha.spoilnot.core.coroutines.ApplicationScope
import com.parcha.spoilnot.core.crash.CrashReporting
import com.parcha.spoilnot.core.prefs.AppPreferences
import com.parcha.spoilnot.feature.grocery.domain.RestockRunner
import com.parcha.spoilnot.feature.notifications.domain.DeviceSync
import com.parcha.spoilnot.feature.notifications.ui.Notifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.OkHttpClient
import timber.log.Timber

@HiltAndroidApp
class SpoilNotApp :
    Application(),
    SingletonImageLoader.Factory {
    @Inject lateinit var restockRunner: RestockRunner

    @Inject lateinit var okHttp: dagger.Lazy<OkHttpClient>

    @Inject lateinit var deviceSync: DeviceSync

    @Inject lateinit var preferences: AppPreferences

    @Inject lateinit var crashReporting: CrashReporting

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // The restock engine runs on every phone while the user is in a household (DESIGN 9.6).
        restockRunner.start()
        Notifications.createChannel(this)
        deviceSync.start()
        // The Crash reports switch (DESIGN 5.6) applies now and whenever it changes.
        preferences.crashReports.onEach(crashReporting::setEnabled).launchIn(appScope)
    }

    /** Recipe images are remote URLs cached on the device (DESIGN 11.6), fetched with the app's OkHttp client. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttp.get() })) }
        .build()
}
