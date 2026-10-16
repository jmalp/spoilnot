package com.parcha.spoilnot.feature.household.domain

import java.time.Instant

enum class Role { Owner, Member }

data class Household(val id: String, val name: String, val ownerUid: String, val inviteCode: String)

data class Member(val uid: String, val displayName: String, val role: Role, val joinedAt: Instant?)

enum class JoinRequestStatus { Pending, Approved, Declined }

data class JoinRequest(
    val uid: String,
    val displayName: String,
    val code: String,
    val status: JoinRequestStatus,
    val requestedAt: Instant?,
)

/** inviteCodes/{code}: what a person sees after typing a code. */
data class InviteCode(val code: String, val householdId: String, val householdName: String, val active: Boolean)

/** The current user's own membership document, as watched by the membership watcher (DESIGN 9.1). */
sealed interface Membership {
    data class Active(val role: Role) : Membership

    /** Confirmed by the server: removed, left, or the household was deleted. */
    data object Gone : Membership
}

object HouseholdRules {
    const val MAX_NAME_LENGTH = 60

    /** Stored in English in v1 (DESIGN 2: strings externalized, English only). */
    const val DEFAULT_GROCERY_LIST_NAME = "Groceries"

    fun isNameValid(name: String): Boolean = name.isNotBlank() && name.trim().length <= MAX_NAME_LENGTH
}
