package com.parcha.spoilnot.feature.household.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.core.ui.messageRes
import com.parcha.spoilnot.feature.household.domain.Household
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.JoinRequest
import com.parcha.spoilnot.feature.household.domain.JoinRequestStatus
import com.parcha.spoilnot.feature.household.domain.Member
import com.parcha.spoilnot.feature.household.domain.Role
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Settings > Household (DESIGN 5.1): members, invite card, join requests and owner actions. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HouseholdViewModel @Inject constructor(
    private val session: SessionManager,
    private val households: HouseholdRepository,
) : ViewModel() {
    data class Content(
        val myUid: String,
        val myRole: Role,
        val household: Household?,
        val members: List<Member>,
        /** Waiting requests; always empty for members. */
        val requests: List<JoinRequest>,
        val joiningPaused: Boolean,
    ) {
        val isOwner get() = myRole == Role.Owner
    }

    data class Actions(val busy: Boolean = false, @StringRes val error: Int? = null)

    val content: StateFlow<Content?> = session.session
        .filterIsInstance<Session.InHousehold>()
        .distinctUntilChanged { a, b -> a.householdId == b.householdId && a.role == b.role }
        .flatMapLatest { me ->
            val hid = me.householdId
            val household = households.observeHousehold(hid)
            val code = household.map { it?.inviteCode }.distinctUntilChanged().flatMapLatest { code ->
                if (code.isNullOrEmpty()) flowOf(null) else households.observeInviteCode(code)
            }
            val requests = if (me.role == Role.Owner) {
                households.observeJoinRequests(hid).map { all -> all.filter { it.status == JoinRequestStatus.Pending } }
            } else {
                flowOf(emptyList())
            }
            combine(household, households.observeMembers(hid), requests, code) { h, members, pending, invite ->
                Content(me.user.uid, me.role, h, members, pending, joiningPaused = invite?.active == false)
            }
        }
        // Losing access ends these listeners; the session moves the user to the gate.
        .catch { }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _actions = MutableStateFlow(Actions())
    val actions: StateFlow<Actions> = _actions

    fun approve(request: JoinRequest) = run { households.approve(hid(), request) }

    fun decline(request: JoinRequest) = run { households.decline(hid(), request.uid) }

    fun rename(name: String) = run { households.rename(household(), name) }

    fun regenerateCode() = run {
        val c = checkNotNull(content.value)
        households.regenerateInviteCode(household(), c.myUid, InviteCodes.generate(), active = !c.joiningPaused)
    }

    fun setJoiningPaused(paused: Boolean) = run { households.setJoiningPaused(household().inviteCode, paused) }

    fun remove(member: Member) = run { households.removeMember(hid(), member.uid) }

    fun makeOwner(member: Member) = run { households.transferOwnership(hid(), myUid(), member.uid) }

    fun leave() = run { households.leave(hid(), myUid()) }

    fun deleteHousehold() = run { households.deleteHousehold(household(), myUid()) }

    fun dismissError() = _actions.update { it.copy(error = null) }

    private fun hid() = household().id

    private fun myUid() = checkNotNull(content.value).myUid

    private fun household() = checkNotNull(content.value?.household)

    private fun run(block: suspend () -> Unit) {
        if (_actions.value.busy) return
        viewModelScope.launch {
            _actions.update { Actions(busy = true) }
            try {
                block()
                _actions.update { it.copy(busy = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actions.update { Actions(busy = false, error = e.messageRes()) }
            }
        }
    }
}
