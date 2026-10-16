package com.parcha.spoilnot.feature.household.domain

import com.parcha.spoilnot.feature.auth.domain.PendingJoin
import kotlinx.coroutines.flow.Flow

/** Households, membership and joining (DESIGN 5.1). Implementations throw [com.parcha.spoilnot.core.error.AppError]. */
interface HouseholdRepository {
    /** Treats a permission error as [Membership.Gone]: removed members can no longer read their own document. */
    fun observeMembership(householdId: String, uid: String): Flow<Membership>

    fun observeHousehold(householdId: String): Flow<Household?>

    /** Ordered by join date. */
    fun observeMembers(householdId: String): Flow<List<Member>>

    /** Owner only. */
    fun observeJoinRequests(householdId: String): Flow<List<JoinRequest>>

    /** The current user's own request, or null once the server confirms there is none. */
    fun observeOwnJoinRequest(householdId: String, uid: String): Flow<JoinRequest?>

    fun observeInviteCode(code: String): Flow<InviteCode?>

    /** Writes the household, owner membership, invite code, default list and profile together. Returns its id. */
    suspend fun createHousehold(uid: String, displayName: String, name: String, inviteCode: String): String

    /** Null when no household uses this code. The code must be well-formed. */
    suspend fun findInviteCode(code: String): InviteCode?

    suspend fun requestToJoin(uid: String, displayName: String, invite: InviteCode)

    /** After approval: points the profile at the household and removes the request. */
    suspend fun completeJoin(uid: String, pending: PendingJoin)

    /** Cancels a waiting request, or dismisses a declined or expired one. */
    suspend fun abandonJoin(uid: String, pending: PendingJoin)

    suspend fun approve(householdId: String, request: JoinRequest)

    suspend fun decline(householdId: String, requesterUid: String)

    suspend fun rename(household: Household, newName: String)

    suspend fun regenerateInviteCode(household: Household, uid: String, newCode: String, active: Boolean)

    suspend fun setJoiningPaused(code: String, paused: Boolean)

    suspend fun removeMember(householdId: String, memberUid: String)

    suspend fun transferOwnership(householdId: String, fromUid: String, toUid: String)

    /** Requires a connection: the cache is cleared right after. */
    suspend fun leave(householdId: String, uid: String)

    /** Deletes every subcollection, then the household (Firestore does not cascade). Requires a connection. */
    suspend fun deleteHousehold(household: Household, uid: String)
}

/** The on-device copy of household data. */
interface LocalData {
    /** Sends pending writes if possible, then wipes the cache (DESIGN 12.2, control 6). */
    suspend fun clear()
}
