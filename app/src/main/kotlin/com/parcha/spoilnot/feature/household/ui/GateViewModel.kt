package com.parcha.spoilnot.feature.household.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.ui.messageRes
import com.parcha.spoilnot.feature.auth.domain.PendingJoin
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.InviteCode
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The household gate (DESIGN 5.1): create a household, or join one with a code and the owner's approval. */
@HiltViewModel
class GateViewModel @Inject constructor(
    private val session: SessionManager,
    private val households: HouseholdRepository,
) : ViewModel() {
    data class UiState(
        val busy: Boolean = false,
        @StringRes val createError: Int? = null,
        @StringRes val joinError: Int? = null,
        @StringRes val requestError: Int? = null,
        /** A found, active code waiting for "Send a request to join?" confirmation. */
        val confirm: InviteCode? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun createHousehold(name: String) = launch(onError = { e -> copy(createError = e.messageRes()) }) {
        val me = signedIn()
        households.createHousehold(me.user.uid, me.displayName, name.trim(), InviteCodes.generate())
    }

    fun lookUpCode(input: String) = launch(onError = { e -> copy(joinError = e.messageRes()) }) {
        val code = InviteCodes.normalize(input)
        if (!InviteCodes.isWellFormed(code)) throw AppError.InviteCodeInactive()
        val invite = households.findInviteCode(code)
        if (invite == null || !invite.active) throw AppError.InviteCodeInactive()
        _state.update { it.copy(confirm = invite) }
    }

    fun dismissConfirm() = _state.update { it.copy(confirm = null) }

    fun sendRequest() {
        val invite = _state.value.confirm ?: return
        launch(onError = { e -> copy(joinError = e.messageRes(), confirm = null) }) {
            val me = signedIn()
            households.requestToJoin(me.user.uid, me.displayName, invite)
            _state.update { it.copy(confirm = null) }
        }
    }

    /** Cancels a waiting request, or dismisses a declined or expired one. */
    fun abandonRequest(pending: PendingJoin) = launch(onError = { e -> copy(requestError = e.messageRes()) }) {
        households.abandonJoin(signedIn().user.uid, pending)
    }

    fun signOut() = launch(onError = { e -> copy(createError = e.messageRes()) }) { session.signOut() }

    private fun signedIn(): Session.NoHousehold =
        session.session.value as? Session.NoHousehold ?: throw AppError.PermissionDenied()

    private fun launch(onError: UiState.(Exception) -> UiState, block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, createError = null, joinError = null, requestError = null) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.onError(e) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
