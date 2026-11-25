package com.parcha.spoilnot

import android.app.Activity
import android.app.Application
import android.os.Bundle
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
        // The restock engine runs on every phone while the user is in a household (DESIGN 9.6), from the first time an
        // activity starts: a process started in the background for a digest starts no household listener (EFF-05).
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) = restockRunner.start()

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
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
