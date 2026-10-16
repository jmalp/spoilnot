package com.parcha.spoilnot.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.JoinRequestStatus
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Shell-wide state: waiting join requests for the owner's Settings badge and Home card (DESIGN 5.1). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShellViewModel @Inject constructor(session: SessionManager, households: HouseholdRepository) : ViewModel() {
    val pendingJoinRequests: StateFlow<Int> = session.session
        .flatMapLatest { s ->
            if (s is Session.InHousehold && s.role == Role.Owner) {
                households.observeJoinRequests(s.householdId).map { all ->
                    all.count {
                        it.status ==
                            JoinRequestStatus.Pending
                    }
                }
            } else {
                flowOf(0)
            }
        }
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
