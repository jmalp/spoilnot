package com.parcha.spoilnot.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.core.coroutines.retryLoading
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.ui.UiMessage
import com.parcha.spoilnot.core.ui.toUiMessage
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.JoinRequestStatus
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull

/** Shell-wide state: waiting join requests for the owner's Settings badge and Home card (DESIGN 5.1). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val session: SessionManager,
    private val households: HouseholdRepository,
    private val deepLinks: DeepLinkRouter,
    writer: HouseholdWriter,
) : ViewModel() {
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)

    /**
     * Writes the server refused after the action returned, from any screen (after v1, review REL-13): collected once
     * here, so a refusal is reported whichever tab or screen is open, and only once. A refusal that lost to another
     * phone names who won (DESIGN 8.5).
     */
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    init {
        writer.errors.onEach { error ->
            val me = myUid()
            val winner = (error as? AppError.LostRace)?.byUid?.takeIf { it != me }?.let { memberName(it) }
            _messages.send(error.toUiMessage({ winner }, me))
        }.launchIn(viewModelScope)
    }

    private fun myUid(): String? = (session.session.value as? Session.InHousehold)?.user?.uid

    /** A member's name for a refusal's message: one read of the member list, only when a race was lost. */
    private suspend fun memberName(uid: String): String? {
        val hid = (session.session.value as? Session.InHousehold)?.householdId ?: return null
        return withTimeoutOrNull(MEMBER_NAME_WAIT_MS) {
            runCatching { households.observeMembers(hid).first().firstOrNull { it.uid == uid }?.displayName }
                .getOrNull()
        }
    }

    /** A notification tap or a shared link waiting to be shown (DESIGN 10.2, 11.1). */
    val deepLink: StateFlow<DeepLink?> = deepLinks.navigation

    /** The shell has shown where [link] goes. */
    fun navigated(link: DeepLink) = deepLinks.consumeNavigation(link)

    fun consume(link: DeepLink) = deepLinks.consume(link)

    fun open(link: DeepLink) = deepLinks.open(link)

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
        .retryLoading(fallback = 0)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

/** How long a refusal's message waits for the member list to name who won. */
private const val MEMBER_NAME_WAIT_MS = 3_000L
