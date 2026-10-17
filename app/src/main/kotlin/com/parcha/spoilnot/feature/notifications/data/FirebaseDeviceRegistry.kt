package com.parcha.spoilnot.feature.notifications.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.parcha.spoilnot.BuildConfig
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.core.firebase.awaitMapped
import com.parcha.spoilnot.feature.notifications.domain.DeviceRegistry
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

@Singleton
class FirebaseDeviceRegistry @Inject constructor(private val firestore: FirestoreProvider) : DeviceRegistry {
    override suspend fun sync(uid: String) {
        val db = firestore.current
        val user = db.document("users/$uid")
        try {
            // Time zone at app start, when it changed (DESIGN 10.2).
            val zone = ZoneId.systemDefault().id
            if (user.get().awaitMapped().getString("timezone") != zone) {
                user.update(mapOf("timezone" to zone, "updatedAt" to FieldValue.serverTimestamp()))
            }
            val token = FirebaseMessaging.getInstance().token.await()
            val device = db.document("users/$uid/devices/${installId()}")
            val current = device.get().awaitMapped()
            if (current.getString("token") != token || current.getString("appVersion") != BuildConfig.VERSION_NAME) {
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
            withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) {
                firestore.current.document("users/$uid/devices/${installId()}").delete().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Device unregister failed")
        }
    }

    private suspend fun installId(): String = FirebaseInstallations.getInstance().id.await()

    private companion object {
        const val UNREGISTER_TIMEOUT_MS = 3_000L
    }
}
