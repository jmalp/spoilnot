package com.parcha.spoilnot.feature.notifications.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.parcha.spoilnot.BuildConfig
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.core.firebase.awaitMapped
import com.parcha.spoilnot.core.firebase.string
import com.parcha.spoilnot.feature.notifications.domain.DeviceRegistry
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

@Singleton
class FirebaseDeviceRegistry @Inject constructor(
    private val firestore: FirestoreProvider,
    private val push: PushIdentity,
) : DeviceRegistry {
    override suspend fun sync(uid: String) {
        val db = firestore.current
        val user = db.document("users/$uid")
        try {
            // Time zone at app start, when it changed (DESIGN 10.2).
            val zone = ZoneId.systemDefault().id
            if (user.get().awaitMapped().string("timezone") != zone) {
                user.update(mapOf("timezone" to zone, "updatedAt" to FieldValue.serverTimestamp()))
            }
            val token = push.token()
            val device = db.document("users/$uid/devices/${push.installId()}")
            val current = device.get().awaitMapped()
            if (current.string("token") != token || current.string("appVersion") != BuildConfig.VERSION_NAME) {
                device.set(
                    mapOf(
                        "token" to token,
                        "platform" to "android",
                        "appVersion" to BuildConfig.VERSION_NAME,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Emulator builds cannot get an FCM token; offline phones retry at the next start.
            Timber.w(e, "Device sync failed")
        }
    }

    override suspend fun unregister(uid: String) {
        try {
            withTimeoutOrNull(TIMEOUT_MS) {
                firestore.current.document("users/$uid/devices/${push.installId()}").delete().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Device unregister failed")
        }
    }

    override suspend fun deleteToken() {
        try {
            withTimeoutOrNull(TIMEOUT_MS) { push.deleteToken() } ?: Timber.w("Push token not deleted in time")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline: the next start without a signed-in user tries again.
            Timber.w(e, "Push token delete failed")
        }
    }

    private companion object {
        const val TIMEOUT_MS = 3_000L
    }
}
