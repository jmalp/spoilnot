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
    /** After v1 (DESIGN 5.9): the reminder also lists the meals the user cooks that day. */
    val cooking: Boolean = true,
) {
    val hour: Int get() = digestMinutes / 60

    companion object {
        const val DEFAULT_MINUTES = 9 * 60
        val ALL_STAGES = setOf(3, 2, 1, 0)

        /**
         * The job sends nothing at or after this local hour (`LATEST_MINUTES` in backend/reminder/src/digest.ts, DESIGN
         * 10.3), and Settings says so (after v1, 2026-11-22).
         */
        const val LATEST_HOUR = 22

        /** The hours Settings offers: the last one is the hour before [LATEST_HOUR]. */
        val HOURS = 5 until LATEST_HOUR
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

    /**
     * While nobody is signed in: invalidates this phone's push token, so a device document left on the server (for
     * example by a sign-out without a connection) gets no more digests and the reminder job removes it.
     */
    suspend fun deleteToken()
}
