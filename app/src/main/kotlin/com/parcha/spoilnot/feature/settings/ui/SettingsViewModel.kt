package com.parcha.spoilnot.feature.settings.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.core.ui.messageRes
import com.parcha.spoilnot.feature.auth.domain.AuthRepository
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val session: SessionManager,
    private val auth: AuthRepository,
    households: HouseholdRepository,
) : ViewModel() {
    data class Content(val displayName: String, val email: String?, val householdName: String?, val memberCount: Int)

    data class UiState(
        val busy: Boolean = false,
        @StringRes val error: Int? = null,
        val emailVerified: Boolean = true,
        val verificationSent: Boolean = false,
    )

    val content: StateFlow<Content?> = session.session
        .filterIsInstance<Session.InHousehold>()
        .flatMapLatest { me ->
            combine(households.observeHousehold(me.householdId), households.observeMembers(me.householdId)) { h, m ->
                Content(me.displayName, me.user.email, h?.name, m.size)
            }
        }
        .catch { }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow(UiState(emailVerified = auth.currentUser?.emailVerified ?: true))
    val state: StateFlow<UiState> = _state

    init {
        // Pick up a verification done in the email app since the last sign-in.
        viewModelScope.launch {
            runCatching {
                auth.reload()
            }.getOrNull()?.let { user -> _state.update { it.copy(emailVerified = user.emailVerified) } }
        }
    }

    fun resendVerification() = run {
        auth.sendEmailVerification()
        _state.update { it.copy(verificationSent = true) }
    }

    fun signOut() = run { session.signOut() }

    fun deleteAccount(password: String) = run { session.deleteAccount(password) }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun run(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.messageRes()) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
