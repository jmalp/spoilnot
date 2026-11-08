package com.parcha.spoilnot.core.cache

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import coil3.SingletonImageLoader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * What the phone keeps outside Firestore from using the app, cleared with the local data (DESIGN 12.2, control 6):
 * the built-in browser's cookies, site storage and cache (someone signed in to a recipe site stays signed in
 * otherwise), and the cached recipe photos.
 */
interface DeviceCaches {
    /** Each part is cleared even when another fails. */
    suspend fun clear()
}

@Singleton
class AndroidDeviceCaches @Inject constructor(@param:ApplicationContext private val context: Context) : DeviceCaches {
    override suspend fun clear() {
        step("browser") { withContext(Dispatchers.Main) { clearBrowser() } }
        step("photos") { clearPhotos() }
    }

    private suspend fun clearBrowser() {
        val cookies = CookieManager.getInstance()
        withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { done -> cookies.removeAllCookies { done.resume(Unit) } }
        } ?: Timber.w("Browser cookies not cleared in time")
        cookies.flush()
        WebStorage.getInstance().deleteAllData()
        WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
        // The disk cache is shared by every web view in the app, so any one clears it.
        WebView(context).run {
            clearCache(true)
            destroy()
        }
    }

    private suspend fun clearPhotos() {
        val images = SingletonImageLoader.get(context)
        images.memoryCache?.clear()
        withContext(Dispatchers.IO) { images.diskCache?.clear() }
    }

    private suspend fun step(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No WebView installed or updating, or storage refused: the next clear tries again.
            Timber.w(e, "Clearing %s failed", name)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 3_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface DeviceCachesModule {
    @Binds
    fun deviceCaches(impl: AndroidDeviceCaches): DeviceCaches
}
