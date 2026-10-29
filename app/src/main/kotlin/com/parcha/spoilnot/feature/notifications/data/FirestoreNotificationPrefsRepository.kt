package com.parcha.spoilnot.feature.notifications.data

import com.google.firebase.firestore.FieldValue
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.core.firebase.snapshotFlow
import com.parcha.spoilnot.feature.notifications.domain.ExpiredMode
import com.parcha.spoilnot.feature.notifications.domain.NotificationPrefs
import com.parcha.spoilnot.feature.notifications.domain.NotificationPrefsRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreNotificationPrefsRepository @Inject constructor(private val firestore: FirestoreProvider) :
    NotificationPrefsRepository {
    override fun observe(uid: String): Flow<NotificationPrefs> = firestore.instance
        .flatMapLatest { db -> db.document("users/$uid").snapshotFlow() }
        .map { snapshot ->
            @Suppress("UNCHECKED_CAST")
            val raw = snapshot.get("notificationPrefs") as? Map<String, Any?> ?: emptyMap()
            NotificationPrefs(
                enabled = raw["enabled"] as? Boolean ?: true,
                digestMinutes = (raw["digestMinutes"] as? Number)?.toInt() ?: NotificationPrefs.DEFAULT_MINUTES,
                stages =
                    (raw["stages"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet()
                        ?: NotificationPrefs.ALL_STAGES,
                expiredMode = ExpiredMode.fromWire(raw["expiredMode"] as? String),
                cooking = raw["cooking"] as? Boolean ?: true,
            )
        }
        .distinctUntilChanged()

    override suspend fun save(uid: String, prefs: NotificationPrefs) {
        // Applied to the cache at once; syncs when online (DESIGN 9.5).
        firestore.current.document("users/$uid").update(
            mapOf(
                "notificationPrefs" to mapOf(
                    "enabled" to prefs.enabled,
                    "digestMinutes" to prefs.digestMinutes,
                    "stages" to prefs.stages.sortedDescending(),
                    "expiredMode" to prefs.expiredMode.wire,
                    "cooking" to prefs.cooking,
                ),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
    }
}
