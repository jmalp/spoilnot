package com.parcha.spoilnot.core.navigation

import androidx.lifecycle.ViewModel
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class RootViewModel @Inject constructor(sessionManager: SessionManager) : ViewModel() {
    val session: StateFlow<Session> = sessionManager.session
}
