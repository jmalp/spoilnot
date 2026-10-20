package com.parcha.spoilnot.feature.settings.ui

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.prefs.AppPreferences
import com.parcha.spoilnot.core.prefs.ThemeMode
import com.parcha.spoilnot.core.ui.messageRes
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.feature.auth.domain.AuthRepository
import com.parcha.spoilnot.feature.auth.domain.ProfileDefaults
import com.parcha.spoilnot.feature.auth.domain.Units
import com.parcha.spoilnot.feature.auth.domain.UserRepository
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.notifications.domain.NotificationPrefs
import com.parcha.spoilnot.feature.notifications.domain.NotificationPrefsRepository
import com.parcha.spoilnot.feature.settings.domain.ExportFormat
import com.parcha.spoilnot.feature.settings.domain.HouseholdExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val session: SessionManager,
    private val auth: AuthRepository,
    private val households: HouseholdRepository,
    notificationPrefs: NotificationPrefsRepository,
    private val preferences: AppPreferences,
    private val users: UserRepository,
    private val exporter: HouseholdExporter,
    private val clock: Clock,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    /** Settings > Appearance and Crash reports (DESIGN 5.6). */
    data class Device(
        val themeMode: ThemeMode = ThemeMode.System,
        val colorTheme: ColorTheme = ColorTheme.Wallpaper,
        val crashReports: Boolean = true,
    )

    val device: StateFlow<Device> =
        combine(preferences.themeMode, preferences.colorTheme, preferences.crashReports, ::Device)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Device())

    val units: StateFlow<Units> = session.session
        .filterIsInstance<Session.InHousehold>()
        .map { it.units }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileDefaults.UNITS)

    /** The export's file name, for the system's Save dialog. */
    fun exportFileName(): String = ExportFormat.fileName(LocalDate.now(clock))

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { preferences.setThemeMode(mode) }

    fun setColorTheme(theme: ColorTheme) = viewModelScope.launch { preferences.setColorTheme(theme) }

    fun setCrashReports(enabled: Boolean) = viewModelScope.launch { preferences.setCrashReports(enabled) }

    /** Starts the app tour again (DESIGN 5.8); the shell picks it up and shows Home. */
    fun takeTour() = viewModelScope.launch { preferences.setTourDone(false) }

    fun setUnits(units: Units) = run {
        val me = session.session.value as? Session.InHousehold ?: return@run
        users.setUnits(me.user.uid, units)
    }

    /** Writes the household's data to [target], a document the user picked in the system's Save dialog. */
    fun export(target: Uri) = run {
        val me = session.session.value as? Session.InHousehold ?: return@run
        val json = exporter.export(me.householdId)
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(target, "wt")?.use { it.write(json.toByteArray()) }
                ?: throw IOException("Cannot write the export")
        }
        _state.update { it.copy(exported = true) }
    }

    fun dismissExported() = _state.update { it.copy(exported = false) }

    val notifications: StateFlow<NotificationPrefs?> = session.session
        .filterIsInstance<Session.InHousehold>()
        .flatMapLatest { notificationPrefs.observe(it.user.uid) }
        .catch { }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    data class Content(
        val displayName: String,
        val email: String?,
        val householdName: String?,
        val memberCount: Int,
        /** Only the owner sees Clear household data (after v1). */
        val isOwner: Boolean = false,
    )

    data class UiState(
        val busy: Boolean = false,
        @StringRes val error: Int? = null,
        val emailVerified: Boolean = true,
        val verificationSent: Boolean = false,
        val exported: Boolean = false,
        /** Clear household data finished; the screen says so and closes its dialogs. */
        val householdCleared: Boolean = false,
    )

    val content: StateFlow<Content?> = session.session
        .filterIsInstance<Session.InHousehold>()
        .flatMapLatest { me ->
            combine(households.observeHousehold(me.householdId), households.observeMembers(me.householdId)) { h, m ->
                Content(me.displayName, me.user.email, h?.name, m.size, me.role == Role.Owner)
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

    /**
     * Clear household data (owner, after v1, DESIGN 5.1): the owner's password is checked first, then everything but
     * the household, its members, invite code and join requests is wiped for everyone.
     */
    fun clearHouseholdData(password: String) = run {
        val me = session.session.value as? Session.InHousehold ?: return@run
        if (me.role != Role.Owner) return@run
        auth.verifyPassword(password)
        households.resetData(me.householdId, Actor(me.user.uid, me.displayName))
        _state.update { it.copy(householdCleared = true) }
    }

    fun dismissHouseholdCleared() = _state.update { it.copy(householdCleared = false) }

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
