package com.parcha.spoilnot.feature.household.domain

import com.parcha.spoilnot.core.coroutines.ApplicationScope
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.feature.auth.domain.AccountValidation
import com.parcha.spoilnot.feature.auth.domain.AuthRepository
import com.parcha.spoilnot.feature.auth.domain.AuthUser
import com.parcha.spoilnot.feature.auth.domain.PendingJoin
import com.parcha.spoilnot.feature.auth.domain.ProfileDefaults
import com.parcha.spoilnot.feature.auth.domain.Units
import com.parcha.spoilnot.feature.auth.domain.UserProfile
import com.parcha.spoilnot.feature.auth.domain.UserRepository
import com.parcha.spoilnot.feature.notifications.domain.DeviceRegistry
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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

    data class InHousehold(
        val user: AuthUser,
        val displayName: String,
        val householdId: String,
        val role: Role,
        val units: Units = ProfileDefaults.UNITS,
    ) : Session
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
 * - Sign-out and account deletion pause all listeners while local data is cleared, and run in the application scope
 *   so that closing a screen cannot stop them halfway.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SessionManager @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val households: HouseholdRepository,
    private val localData: LocalData,
    private val devices: DeviceRegistry,
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

    /**
     * Clears this device's copy of the household before signing out (DESIGN 12.2, control 6). Runs to the end even
     * when the screen that asked is closed (see [untilDone]).
     */
    suspend fun signOut() = untilDone {
        val uid = auth.currentUser?.uid
        shuttingDown.value = true
        try {
            // This phone stops receiving the user's digests (DESIGN 10.2).
            if (uid != null) bestEffort { devices.unregister(uid) }
            bestEffort { localData.clear() }
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
     * The sign-in is refreshed only at the very end if Firebase requires it, after all Firestore writes. Once the
     * password is checked it runs to the end even when the screen that asked is closed (see [untilDone]).
     */
    suspend fun deleteAccount(password: String) {
        val uid = auth.currentUser?.uid ?: return
        // Decide from a settled session, not a momentary Loading state while listeners reconnect.
        val current = withTimeoutOrNull(SETTLE_TIMEOUT_MS) { session.first { it !is Session.Loading } }
            ?: throw AppError.Offline()
        if (current is Session.InHousehold && current.role == Role.Owner) throw AppError.OwnerMustTransferFirst()
        auth.verifyPassword(password)
        untilDone {
            shuttingDown.value = true
            try {
                when (current) {
                    is Session.InHousehold -> households.leave(current.householdId, uid)
                    is Session.NoHousehold -> current.join?.let { households.abandonJoin(uid, it.pending) }
                    else -> Unit
                }
                users.deleteProfile(uid)
                bestEffort { localData.clear() }
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
    }

    /**
     * Runs [block] in the application scope and waits for it. Sign-out and account deletion start by turning the
     * session to Loading, which removes the screen that asked and cancels its coroutines; the work belongs to the
     * session, not to that screen, so it must finish anyway. The caller still gets the result or the error.
     */
    private suspend fun <T> untilDone(block: suspend () -> T): T = scope.async { block() }.await()

    /** A cleanup step whose failure must not stop the next one. Cancellation is never swallowed. */
    private suspend fun bestEffort(step: suspend () -> Unit) {
        try {
            step()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best effort: the next step runs anyway.
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
            // Only a permission refusal changes the screen. Any other error (the quota running out, the network, a
            // bad document) keeps the last session on screen, cached data included, while it listens again
            // (DESIGN 6.7, 7.5; after v1, REL-02).
            if (cause.isPermissionRefusal()) emit(Session.Loading)
            delay(backoffMillis(attempt))
            true
        }

    private fun watchMembership(user: AuthUser, profile: UserProfile, householdId: String): Flow<Session> =
        households.observeMembership(householdId, user.uid).transformLatest { membership ->
            when (membership) {
                is Membership.Active -> emit(
                    Session.InHousehold(user, profile.displayName, householdId, membership.role, profile.units),
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
            if (request?.status ==
                JoinRequestStatus.Approved
            ) {
                households.completeJoin(user.uid, profile.displayName, pending)
            }
        }

    private fun onTransition(session: Session) {
        when (session) {
            is Session.InHousehold -> lastHouseholdId.set(session.householdId)

            is Session.NoHousehold -> {
                if (lastHouseholdId.getAndSet(null) != null) scope.launch { bestEffort { localData.clear() } }
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

/**
 * The server refused the read (`PERMISSION_DENIED` or `UNAUTHENTICATED`): the rules no longer let this user see the
 * profile or membership, so the screen must not keep showing it.
 */
internal fun Throwable.isPermissionRefusal(): Boolean = this is AppError.PermissionDenied
