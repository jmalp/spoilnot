package com.parcha.spoilnot.feature.notifications.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.notifications.domain.NotificationPrefs
import com.parcha.spoilnot.feature.notifications.domain.NotificationPrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings > Notifications (DESIGN 10.2): on or off, hour, stages and expired mode. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val session: SessionManager,
    private val repository: NotificationPrefsRepository,
) : ViewModel() {
    private fun uid(): String? = when (val s = session.session.value) {
        is Session.InHousehold -> s.user.uid
        is Session.NoHousehold -> s.user.uid
        else -> null
    }

    val prefs: StateFlow<NotificationPrefs?> = session.session
        .map { s -> (s as? Session.InHousehold)?.user?.uid ?: (s as? Session.NoHousehold)?.user?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid -> if (uid == null) flowOf(null) else repository.observe(uid) }
        .catch { }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(change: (NotificationPrefs) -> NotificationPrefs) {
        val uid = uid() ?: return
        val current = prefs.value ?: return
        viewModelScope.launch { runCatching { repository.save(uid, change(current)) } }
    }
}
