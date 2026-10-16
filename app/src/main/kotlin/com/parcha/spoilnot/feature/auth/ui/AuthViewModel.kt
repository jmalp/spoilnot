package com.parcha.spoilnot.feature.auth.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.core.ui.messageRes
import com.parcha.spoilnot.feature.auth.domain.AuthRepository
import com.parcha.spoilnot.feature.household.domain.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sign in, sign up and password reset. Success shows up as a new session, which replaces these screens. */
@HiltViewModel
class AuthViewModel @Inject constructor(private val session: SessionManager, private val auth: AuthRepository) :
    ViewModel() {
    data class UiState(val busy: Boolean = false, @StringRes val error: Int? = null, val resetSentTo: String? = null)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun signIn(email: String, password: String) = launch { session.signIn(email, password) }

    fun signUp(displayName: String, email: String, password: String) =
        launch { session.signUp(displayName, email, password) }

    fun sendPasswordReset(email: String) = launch {
        auth.sendPasswordReset(email.trim())
        _state.update { it.copy(resetSentTo = email.trim()) }
    }

    /** Called when moving between screens so a message from one does not show on the next. */
    fun resetMessages() = _state.update { it.copy(error = null, resetSentTo = null) }

    private fun launch(block: suspend () -> Unit) {
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
