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
    /** The requester's sign-in address, masked ("ro***@example.com"); null from builds before it was written. */
    val emailMasked: String? = null,
)

/** inviteCodes/{code}: what a person sees after typing a code. */
data class InviteCode(val code: String, val householdId: String, val householdName: String)

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

/**
 * The requester's email as the owner sees it on a join request (after v1): the first two characters of the name
 * part (one when it has two or fewer), three stars, and the domain, as in "ro***@example.com". The rules compute the
 * same from the sign-in's address, so a request can't show someone else's. Null for an address the rules might count
 * differently (not one @, or a name starting with characters outside ASCII): the request is then sent without it.
 */
object EmailMask {
    fun mask(email: String?): String? {
        if (email == null) return null
        val at = email.indexOf('@')
        if (at <= 0 || at != email.lastIndexOf('@') || at == email.length - 1) return null
        val local = email.substring(0, at)
        val kept = local.take(if (local.length > 2) 2 else 1)
        if (kept.any { it.code !in 0x21..0x7E }) return null
        return kept + "***" + email.substring(at)
    }
}
