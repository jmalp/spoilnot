package com.parcha.spoilnot.feature.household.domain

import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.feature.auth.domain.AuthRepository
import com.parcha.spoilnot.feature.auth.domain.AuthUser
import com.parcha.spoilnot.feature.auth.domain.PendingJoin
import com.parcha.spoilnot.feature.auth.domain.Units
import com.parcha.spoilnot.feature.auth.domain.UserProfile
import com.parcha.spoilnot.feature.auth.domain.UserRepository
import com.parcha.spoilnot.feature.notifications.domain.DeviceRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.yield

/** Fakes for [SessionManager] and the classes that follow its session. Each logs its calls to a shared list, in order. */
internal class FakeAuth(private val log: MutableList<String>) : AuthRepository {
    val user = MutableStateFlow<AuthUser?>(null)
    var signUpResult: AuthUser? = null
    override val authUser: Flow<AuthUser?> = user
    override val currentUser: AuthUser? get() = user.value

    override suspend fun signUp(displayName: String, email: String, password: String) {
        user.value = signUpResult
    }

    override suspend fun signIn(email: String, password: String) {
        signInResult?.let { user.value = it }
    }

    override suspend fun sendPasswordReset(email: String) = Unit

    override suspend fun sendEmailVerification() = Unit

    override suspend fun reload(): AuthUser? = user.value

    /** Who signs in: the account [signIn] signs in to (null keeps the current user). */
    var signInResult: AuthUser? = null

    /** When set, the password check waits for it, like a request to the server. */
    var verifyGate: CompletableDeferred<Unit>? = null

    override suspend fun verifyPassword(password: String) {
        verifyGate?.await()
        if (password != "password1") throw AppError.InvalidCredentials()
    }

    override suspend fun reauthenticate(password: String) = verifyPassword(password)

    override fun signOut() {
        log += "signOut"
        user.value = null
    }

    override suspend fun deleteAccount() {
        log += "delete"
        user.value = null
    }
}

/** Server state of users/{uid}; null means the document does not exist. */
internal class FakeUsers : UserRepository {
    private val docs = mutableMapOf<String, MutableStateFlow<UserProfile?>>()
    val deleted = mutableSetOf<String>()

    fun doc(uid: String) = docs.getOrPut(uid) { MutableStateFlow(null) }

    fun put(profile: UserProfile) {
        doc(profile.uid).value = profile
    }

    fun profile(uid: String) = doc(uid).value

    /** When set, the profile listener fails with it, as a refusal from the server would (it can fail again on retry). */
    val failure = MutableStateFlow<Throwable?>(null)

    /** How many times the profile listener was started. */
    var listens = 0

    override fun observeProfile(uid: String): Flow<UserProfile?> =
        combine(doc(uid), failure) { profile, error -> if (error != null) throw error else profile }
            .onStart { listens++ }

    override suspend fun createProfileIfMissing(uid: String, displayName: String) {
        if (doc(uid).value == null) doc(uid).value = UserProfile(uid, displayName, null, null)
    }

    override suspend fun clearHousehold(uid: String) {
        doc(uid).value = doc(uid).value?.copy(householdId = null)
    }

    override suspend fun deleteProfile(uid: String) {
        deleted += uid
        doc(uid).value = null
    }

    override suspend fun setUnits(uid: String, units: Units) = Unit
}

internal class FakeHouseholds(private val users: FakeUsers) : HouseholdRepository {
    private val memberships = mutableMapOf<Pair<String, String>, MutableStateFlow<Membership?>>()
    val ownRequest = MutableStateFlow<JoinRequest?>(null)

    /** When set, leaving waits for it, like a write waiting for the server. */
    var leaveGate: CompletableDeferred<Unit>? = null

    fun membership(hid: String, uid: String) = memberships.getOrPut(hid to uid) { MutableStateFlow(null) }

    /** When set, the membership listener fails with it. */
    val membershipFailure = MutableStateFlow<Throwable?>(null)

    override fun observeMembership(householdId: String, uid: String): Flow<Membership> =
        combine(membership(householdId, uid).filterNotNull(), membershipFailure) { membership, error ->
            if (error != null) throw error else membership
        }

    override fun observeOwnJoinRequest(householdId: String, uid: String): Flow<JoinRequest?> = ownRequest.map { it }

    override suspend fun completeJoin(uid: String, displayName: String, pending: PendingJoin) {
        users.put(UserProfile(uid, users.profile(uid)!!.displayName, pending.householdId, null))
        ownRequest.value = null
    }

    override suspend fun abandonJoin(uid: String, pending: PendingJoin) {
        users.put(users.profile(uid)!!.copy(pendingJoin = null))
        ownRequest.value = null
    }

    override suspend fun leave(householdId: String, uid: String) {
        leaveGate?.await()
        membership(householdId, uid).value = Membership.Gone
        users.clearHousehold(uid)
    }

    override fun observeHousehold(householdId: String): Flow<Household?> = error("unused")

    override fun observeMembers(householdId: String): Flow<List<Member>> = error("unused")

    override fun observeJoinRequests(householdId: String): Flow<List<JoinRequest>> = error("unused")

    override fun observeInviteCode(code: String): Flow<InviteCode?> = error("unused")

    override suspend fun createHousehold(uid: String, displayName: String, name: String, inviteCode: String) =
        error("unused")

    override suspend fun findInviteCode(code: String): InviteCode? = error("unused")

    override suspend fun requestToJoin(uid: String, displayName: String, invite: InviteCode, email: String?) =
        error("unused")

    override suspend fun approve(householdId: String, request: JoinRequest) = error("unused")

    override suspend fun decline(householdId: String, requesterUid: String) = error("unused")

    override suspend fun rename(household: Household, newName: String) = error("unused")

    override suspend fun regenerateInviteCode(household: Household, uid: String, newCode: String, active: Boolean) =
        error("unused")

    override suspend fun setJoiningPaused(code: String, paused: Boolean) = error("unused")

    override suspend fun removeMember(householdId: String, memberUid: String) = error("unused")

    override suspend fun transferOwnership(householdId: String, fromUid: String, toUid: String) = error("unused")

    override suspend fun deleteHousehold(household: Household, uid: String) = error("unused")

    override suspend fun resetData(householdId: String, actor: Actor): ResetOutcome = error("unused")
}

internal class FakeDevices(private val log: MutableList<String>) : DeviceRegistry {
    /** When set, unregistering waits for it, like the device delete waiting for the server. */
    var unregisterGate: CompletableDeferred<Unit>? = null

    /** When set, unregistering fails with it after the wait. */
    var unregisterError: Exception? = null

    override suspend fun sync(uid: String) {
        log += "sync $uid"
    }

    override suspend fun unregister(uid: String) {
        unregisterGate?.await()
        unregisterError?.let { throw it }
        log += "unregister $uid"
    }

    override suspend fun deleteToken() {
        log += "deleteToken"
    }
}

internal class FakeLocalData(private val log: MutableList<String>) : LocalData {
    var clears = 0

    /** False while this phone has changes the server hasn't received. */
    var synced = true

    override suspend fun flushPendingWrites(): Boolean {
        yield()
        return synced
    }

    override suspend fun clear() {
        // The real one suspends before it wipes anything (it first waits for pending writes).
        yield()
        clears++
        log += "clear"
    }
}
