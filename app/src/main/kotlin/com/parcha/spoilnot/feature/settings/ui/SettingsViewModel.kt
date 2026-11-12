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
import com.parcha.spoilnot.feature.household.domain.ResetOutcome
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

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { preferences.setThemeMode(mode) }

    fun setColorTheme(theme: ColorTheme) = viewModelScope.launch { preferences.setColorTheme(theme) }

    fun setCrashReports(enabled: Boolean) = viewModelScope.launch { preferences.setCrashReports(enabled) }

    /** Starts the app tour again (DESIGN 5.8); the shell picks it up and shows Home. */
    fun takeTour() = viewModelScope.launch { preferences.setTourDone(false) }

    fun setUnits(units: Units) = run {
        val me = session.session.value as? Session.InHousehold ?: return@run
        users.setUnits(me.user.uid, units)
    }

    /** The export read from the server, waiting for the system's Save dialog. */
    private var pendingExport: String? = null

    /**
     * Export (DESIGN 13.4): reads the household's data, the person's profile and the activity log from the server, then
     * asks the screen to open the Save dialog ([UiState.exportReady]). Offline it fails with "You're offline" and
     * saves nothing, rather than a file of whatever this phone has cached.
     */
    fun export() = run(exportFailed) {
        val me = session.session.value as? Session.InHousehold ?: return@run
        pendingExport = exporter.export(me.householdId, me.user.uid)
        _state.update { it.copy(exportReady = ExportFormat.fileName(LocalDate.now(clock))) }
    }

    fun exportDialogOpened() = _state.update { it.copy(exportReady = null) }

    fun cancelExport() {
        pendingExport = null
    }

    /**
     * Writes the export to [target], a document the user picked in the system's Save dialog. When the export read
     * before the dialog is gone (the process was ended meanwhile), it is read again.
     */
    fun saveExport(target: Uri) = run(exportFailed) {
        val me = session.session.value as? Session.InHousehold ?: return@run
        val json = pendingExport ?: exporter.export(me.householdId, me.user.uid)
        pendingExport = null
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(target, "wt")?.use { it.write(json.toByteArray()) }
                ?: throw IOException("Cannot write the export")
        }
        _state.update { it.copy(exported = true) }
    }

    fun dismissExported() = _state.update { it.copy(exported = false, exportError = null) }

    /** Export's failures go to a snackbar next to the button, not the top of the screen. */
    private val exportFailed: (UiState, Int) -> UiState = { s, error -> s.copy(exportError = error) }

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
        /** Export was read from the server; the screen opens the Save dialog with this file name. */
        val exportReady: String? = null,
        val exported: Boolean = false,
        /** Why Export failed, such as "You're offline" (shown as a snackbar). */
        @StringRes val exportError: Int? = null,
        /** Clear household data finished, or finishes once online; the screen says which and closes its dialogs. */
        val householdCleared: ResetOutcome? = null,
        /** Sign out found changes that haven't synced yet and asks before throwing them away (after v1). */
        val askSignOutUnsynced: Boolean = false,
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

    /** Asks first when changes haven't synced yet; [evenIfUnsynced] is the answer "Sign out anyway". */
    fun signOut(evenIfUnsynced: Boolean = false) = run {
        _state.update { it.copy(askSignOutUnsynced = false) }
        if (!session.signOut(evenIfUnsynced)) _state.update { it.copy(askSignOutUnsynced = true) }
    }

    fun dismissSignOutUnsynced() = _state.update { it.copy(askSignOutUnsynced = false) }

    fun deleteAccount(password: String) = run { session.deleteAccount(password) }

    /**
     * Clear household data (owner, after v1, DESIGN 5.1): the owner's password is checked first, then everything but
     * the household, its members, invite code and join requests is wiped for everyone.
     */
    fun clearHouseholdData(password: String) = run {
        val me = session.session.value as? Session.InHousehold ?: return@run
        if (me.role != Role.Owner) return@run
        auth.verifyPassword(password)
        val outcome = households.resetData(me.householdId, Actor(me.user.uid, me.displayName))
        _state.update { it.copy(householdCleared = outcome) }
    }

    fun dismissHouseholdCleared() = _state.update { it.copy(householdCleared = null) }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun run(
        onError: (UiState, Int) -> UiState = { s, error -> s.copy(error = error) },
        block: suspend () -> Unit,
    ) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, exportError = null) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { onError(it, e.messageRes()) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
