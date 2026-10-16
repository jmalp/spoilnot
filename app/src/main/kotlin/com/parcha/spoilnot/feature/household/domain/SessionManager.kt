package com.parcha.spoilnot.feature.household.domain

import com.parcha.spoilnot.core.coroutines.ApplicationScope
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.feature.auth.domain.AccountValidation
import com.parcha.spoilnot.feature.auth.domain.AuthRepository
import com.parcha.spoilnot.feature.auth.domain.AuthUser
import com.parcha.spoilnot.feature.auth.domain.PendingJoin
import com.parcha.spoilnot.feature.auth.domain.UserProfile
import com.parcha.spoilnot.feature.auth.domain.UserRepository
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Where the signed-in user stands. Drives the top-level screen (sign-in, household gate or the app). */
sealed interface Session {
    data object Loading : Session

    data object SignedOut : Session

    /** Signed in without a household: the household gate, possibly with a join request in flight. */
    data class NoHousehold(val user: AuthUser, val displayName: String, val join: JoinState?) : Session

    data class InHousehold(val user: AuthUser, val displayName: String, val householdId: String, val role: Role) :
        Session
}

/** The user's one join request (DESIGN 5.1). An approved request completes on its own. */
sealed interface JoinState {
    val pending: PendingJoin

    data class Waiting(override val pending: PendingJoin) : JoinState

    data class Declined(override val pending: PendingJoin) : JoinState

    /** The request disappeared without an answer, for example pruned after 7 days. */
    data class Expired(override val pending: PendingJoin) : JoinState
}

/**
 * The session state machine and membership watcher (DESIGN 5.1, 9.1).
 *
 * - Creates the profile if it is missing (for example after an interrupted sign-up).
 * - Completes an approved join request.
 * - When the membership disappears (removed, or the household was deleted) it clears the profile pointer, which
 *   returns the user to the gate; whenever a household is left for any reason the local cache is cleared.
 * - Sign-out and account deletion pause all listeners while local data is cleared.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SessionManager @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val households: HouseholdRepository,
    private val localData: LocalData,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val shuttingDown = MutableStateFlow(false)
    private val pendingDisplayName = AtomicReference<String?>(null)

    /** The household this process last saw the user in; its cache is cleared once they are no longer in it. */
    private val lastHouseholdId = AtomicReference<String?>(null)

    val session: StateFlow<Session> =
        combine(auth.authUser.distinctUntilChangedBy { it?.uid }, shuttingDown) { user, busy -> user to busy }
            .flatMapLatest { (user, busy) ->
                when {
                    busy -> flowOf(Session.Loading)
                    user == null -> flowOf(Session.SignedOut)
                    else -> sessionFor(user)
                }
            }
            .distinctUntilChanged()
            .onEach(::onTransition)
            .stateIn(scope, SharingStarted.Eagerly, Session.Loading)

    suspend fun signUp(displayName: String, email: String, password: String) {
        pendingDisplayName.set(displayName.trim())
        auth.signUp(displayName.trim(), email.trim(), password)
    }

    suspend fun signIn(email: String, password: String) = auth.signIn(email.trim(), password)

    /** Clears this device's copy of the household before signing out (DESIGN 12.2, control 6). */
    suspend fun signOut() {
        shuttingDown.value = true
        try {
            runCatching { localData.clear() }
            auth.signOut()
        } finally {
            pendingDisplayName.set(null)
            lastHouseholdId.set(null)
            shuttingDown.value = false
        }
    }

    /**
     * Deletes the account (DESIGN 5.1): checks the password, leaves the household, deletes the profile and devices,
     * clears local data and deletes the sign-in. An owner must transfer ownership or delete the household first.
     * The sign-in is refreshed only at the very end if Firebase requires it, after all Firestore writes.
     */
    suspend fun deleteAccount(password: String) {
        val uid = auth.currentUser?.uid ?: return
        // Decide from a settled session, not a momentary Loading state while listeners reconnect.
        val current = withTimeoutOrNull(SETTLE_TIMEOUT_MS) { session.first { it !is Session.Loading } }
            ?: throw AppError.Offline()
        if (current is Session.InHousehold && current.role == Role.Owner) throw AppError.OwnerMustTransferFirst()
        auth.verifyPassword(password)
        shuttingDown.value = true
        try {
            when (current) {
                is Session.InHousehold -> households.leave(current.householdId, uid)
                is Session.NoHousehold -> current.join?.let { households.abandonJoin(uid, it.pending) }
                else -> Unit
            }
            users.deleteProfile(uid)
            runCatching { localData.clear() }
            try {
                auth.deleteAccount()
            } catch (e: AppError.RecentLoginRequired) {
                // Firestore work is finished, so refreshing the sign-in now cannot affect it.
                auth.reauthenticate(password)
                auth.deleteAccount()
            }
        } finally {
            pendingDisplayName.set(null)
            lastHouseholdId.set(null)
            shuttingDown.value = false
        }
    }

    private fun sessionFor(user: AuthUser): Flow<Session> = users.observeProfile(user.uid)
        .flatMapLatest { profile ->
            when {
                profile == null -> flow {
                    emit(Session.Loading)
                    users.createProfileIfMissing(user.uid, displayNameFor(user))
                }

                profile.householdId != null -> watchMembership(user, profile, profile.householdId)

                profile.pendingJoin != null -> watchJoinRequest(user, profile, profile.pendingJoin)

                else -> flowOf(Session.NoHousehold(user, profile.displayName, join = null))
            }
        }
        .retryWhen { cause, attempt ->
            if (cause is CancellationException) return@retryWhen false
            emit(Session.Loading)
            delay(backoffMillis(attempt))
            true
        }

    private fun watchMembership(user: AuthUser, profile: UserProfile, householdId: String): Flow<Session> =
        households.observeMembership(householdId, user.uid).transformLatest { membership ->
            when (membership) {
                is Membership.Active -> emit(
                    Session.InHousehold(user, profile.displayName, householdId, membership.role),
                )

                Membership.Gone -> {
                    // Removed, or the household was deleted. Clearing the pointer moves the profile (and so the
                    // session) to the gate within moments; the cache is cleared on that transition.
                    lastHouseholdId.compareAndSet(null, householdId)
                    try {
                        users.clearHousehold(user.uid)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emit(Session.NoHousehold(user, profile.displayName, join = null))
                    }
                }
            }
        }

    private fun watchJoinRequest(user: AuthUser, profile: UserProfile, pending: PendingJoin): Flow<Session> =
        households.observeOwnJoinRequest(pending.householdId, user.uid).transformLatest { request ->
            val join = when (request?.status) {
                null -> JoinState.Expired(pending)
                JoinRequestStatus.Pending, JoinRequestStatus.Approved -> JoinState.Waiting(pending)
                JoinRequestStatus.Declined -> JoinState.Declined(pending)
            }
            emit(Session.NoHousehold(user, profile.displayName, join))
            if (request?.status == JoinRequestStatus.Approved) households.completeJoin(user.uid, pending)
        }

    private fun onTransition(session: Session) {
        when (session) {
            is Session.InHousehold -> lastHouseholdId.set(session.householdId)

            is Session.NoHousehold -> {
                if (lastHouseholdId.getAndSet(null) != null) scope.launch { runCatching { localData.clear() } }
            }

            Session.SignedOut -> lastHouseholdId.set(null)

            Session.Loading -> Unit
        }
    }

    private fun displayNameFor(user: AuthUser): String = pendingDisplayName.get()
        ?: user.displayName?.takeIf { it.isNotBlank() }
        ?: AccountValidation.fallbackDisplayName(user.email)

    private fun backoffMillis(attempt: Long): Long = (1_000L shl attempt.coerceAtMost(5).toInt())

    private companion object {
        const val SETTLE_TIMEOUT_MS = 10_000L
    }
}
