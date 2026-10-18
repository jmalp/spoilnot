package com.parcha.spoilnot.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.BuildConfig
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.update.domain.AppConfig
import com.parcha.spoilnot.feature.update.domain.AppConfigRepository
import com.parcha.spoilnot.feature.update.domain.UpdateGate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RootViewModel @Inject constructor(sessionManager: SessionManager, appConfig: AppConfigRepository) :
    ViewModel() {
    val session: StateFlow<Session> = sessionManager.session

    /** config/app when this build is too old for it (DESIGN 13.3). Readable once signed in. */
    val updateRequired: StateFlow<AppConfig?> = sessionManager.session
        .map { it !is Session.SignedOut && it !is Session.Loading }
        .distinctUntilChanged()
        .flatMapLatest { signedIn -> if (signedIn) appConfig.observe() else flowOf(null) }
        .map { config -> config?.takeIf { UpdateGate.required(it, BuildConfig.VERSION_CODE) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
