package com.parcha.spoilnot.feature.notifications.domain

import kotlinx.coroutines.flow.Flow

enum class ExpiredMode(val wire: String) {
    Once("once"),
    Daily("daily"),
    Never("never"),
    ;

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value } ?: Once
    }
}

/** users/{uid}.notificationPrefs (DESIGN 8.2). The UI offers whole hours. */
data class NotificationPrefs(
    val enabled: Boolean = true,
    val digestMinutes: Int = DEFAULT_MINUTES,
    /** Subset of 3, 2, 1, 0 days before. */
    val stages: Set<Int> = ALL_STAGES,
    val expiredMode: ExpiredMode = ExpiredMode.Once,
) {
    val hour: Int get() = digestMinutes / 60

    companion object {
        const val DEFAULT_MINUTES = 9 * 60
        val ALL_STAGES = setOf(3, 2, 1, 0)

        /** The job does not send after 22:00 (DESIGN 10.3), so the last offered hour is 21. */
        val HOURS = 5..21
    }
}

interface NotificationPrefsRepository {
    fun observe(uid: String): Flow<NotificationPrefs>

    suspend fun save(uid: String, prefs: NotificationPrefs)
}

/**
 * This phone's push registration (DESIGN 10.2): the FCM token in users/{uid}/devices/{installId} and the IANA time
 * zone on the profile, written only when they change.
 */
interface DeviceRegistry {
    suspend fun sync(uid: String)

    /** On sign-out, so the signed-out phone stops receiving this user's digests. */
    suspend fun unregister(uid: String)
}
