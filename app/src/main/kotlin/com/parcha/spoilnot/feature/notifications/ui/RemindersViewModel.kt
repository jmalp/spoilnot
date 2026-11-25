package com.parcha.spoilnot.feature.notifications.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.core.coroutines.retryLoading
import com.parcha.spoilnot.core.prefs.AppPreferences
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.notifications.domain.NotificationAccess
import com.parcha.spoilnot.feature.notifications.domain.NotificationPrefs
import com.parcha.spoilnot.feature.notifications.domain.NotificationPrefsRepository
import com.parcha.spoilnot.feature.notifications.domain.NotificationPrompt
import com.parcha.spoilnot.feature.notifications.domain.ReminderAccess
import com.parcha.spoilnot.feature.notifications.domain.YoureSetSender
import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import com.parcha.spoilnot.feature.pantry.ui.todayFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Reminders on this phone, for the whole household app (DESIGN 5.4; after v1, review UX-02): the explanation that asks
 * for the permission, on whichever tab is open, Home's "Reminders are off on this phone · Turn on", and "You're set"
 * after Turn on. The rule is [NotificationPrompt]; this only gathers what it needs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RemindersViewModel @Inject constructor(
    session: SessionManager,
    pantry: PantryRepository,
    prefsRepository: NotificationPrefsRepository,
    private val preferences: AppPreferences,
    private val access: NotificationAccess,
    private val youreSet: YoureSetSender,
    clock: Clock,
) : ViewModel() {
    data class UiState(
        /** Show the explanation now. */
        val ask: Boolean = false,
        /** Home's line: the member wants reminders and this phone blocks them. */
        val offOnThisPhone: Boolean = false,
        /** The reminder hour, for the explanation. */
        val hour: Int = NotificationPrefs.DEFAULT_MINUTES / 60,
    )

    /** Read again on every resume: the system settings can change it while the app is away. */
    private val allowed = MutableStateFlow(access.allowed())

    private val me = session.session
        .map { (it as? Session.InHousehold)?.let { s -> s.householdId to s.user.uid } }
        .distinctUntilChanged()

    private val prefs: Flow<NotificationPrefs?> = me.flatMapLatest { me ->
        if (me == null) {
            flowOf(null)
        } else {
            prefsRepository.observe(me.second).retryLoading<NotificationPrefs?>(fallback = null).onStart { emit(null) }
        }
    }

    private val explained: Flow<Boolean> = preferences.notificationExplainerShown

    /**
     * The dated food when the household first loaded from the server, and now. The pantry is listened to only while
     * the explanation may still be due, so a phone that has answered adds nothing; Home's own listener is shared.
     */
    private val dated: Flow<Dated> = combine(me, explained) { m, done -> m?.first?.takeUnless { done } }
        .distinctUntilChanged()
        .flatMapLatest { hid ->
            if (hid == null) {
                flowOf(Dated(null, 0))
            } else {
                combine(pantry.observe(hid).retryLoading(), todayFlow(clock)) { data, today ->
                    data.fromCache to NotificationPrompt.datedFood(data.batches, today)
                }.scan(Dated(null, 0)) { acc, (fromCache, count) ->
                    Dated(acc.atOpen ?: count.takeUnless { fromCache }, count)
                }
            }
        }

    /** The app tour goes first (DESIGN 5.8): on a new phone it starts on Home, and two things at once is one too many. */
    private val tourDone: Flow<Boolean> = preferences.tourDone

    val state: StateFlow<UiState> =
        combine(allowed, prefs, explained, dated, tourDone) { allowed, prefs, explained, dated, tourDone ->
            val access = ReminderAccess(allowed, prefs?.enabled, explained)
            UiState(
                ask = tourDone && NotificationPrompt.shouldAsk(access, dated.atOpen, dated.now),
                offOnThisPhone = NotificationPrompt.offOnThisPhone(access),
                hour = prefs?.hour ?: NotificationPrefs.DEFAULT_MINUTES / 60,
            ) to access
        }
            .onEach { (_, access) -> if (NotificationPrompt.explainedWithoutAsking(access)) markExplained() }
            .map { it.first }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /** Back on screen: notifications may have been turned on or off in the system settings. */
    fun refresh() {
        allowed.value = access.allowed()
    }

    /** Not now: never explained again on this phone; Home's line offers Turn on from now on. */
    fun later() = markExplained()

    /** Turn on was tapped (from the explanation or Home): the explanation is answered whatever the system says. */
    fun turnOnStarted() = markExplained()

    /** The system answered, or the person is back from its settings: check again, and say "You're set" if it worked. */
    fun turnOnFinished() {
        refresh()
        if (allowed.value) viewModelScope.launch { youreSet.afterTurnOn() }
    }

    private fun markExplained() {
        viewModelScope.launch { preferences.markNotificationExplainerShown() }
    }

    private data class Dated(val atOpen: Int?, val now: Int)
}
