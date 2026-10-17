package com.parcha.spoilnot.feature.auth.domain

/** The signed-in Firebase Auth account, without Firebase types. */
data class AuthUser(val uid: String, val email: String?, val displayName: String?, val emailVerified: Boolean)

/** users/{uid}: the private profile (DESIGN 8.2). */
data class UserProfile(
    val uid: String,
    val displayName: String,
    val householdId: String?,
    val pendingJoin: PendingJoin?,
    /** Which units the unit menu offers first for new products (Settings > Appearance). */
    val units: Units = ProfileDefaults.UNITS,
)

/** Where the user's one waiting join request was sent (DESIGN 5.1). */
data class PendingJoin(val householdId: String, val householdName: String, val code: String)

enum class Units { Metric, Imperial }

/** Defaults for a new profile. The owner chose imperial (docs/DECISIONS.md). */
object ProfileDefaults {
    val UNITS = Units.Imperial
    const val LOCALE = "en"
    const val DIGEST_MINUTES = 9 * 60
    val STAGES = listOf(3, 2, 1, 0)
    const val EXPIRED_MODE = "once"
}
