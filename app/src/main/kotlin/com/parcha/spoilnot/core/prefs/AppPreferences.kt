package com.parcha.spoilnot.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_preferences")

/** Settings > Appearance (DESIGN 5.6). */
enum class ThemeMode { System, Light, Dark }

/** Small on-device settings that are not household data (DESIGN 7.3: androidx.datastore). */
@Singleton
class AppPreferences @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val explainerShown = booleanPreferencesKey("notification_explainer_shown")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val crashReportsKey = booleanPreferencesKey("crash_reports")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[themeModeKey] } ?: ThemeMode.System
    }

    /** Material You colors on Android 12 and later, on by default (DESIGN 6.4). */
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[dynamicColorKey] != false }

    /** Crash reports, on by default with an off switch (DESIGN 2 telemetry). */
    val crashReports: Flow<Boolean> = context.dataStore.data.map { it[crashReportsKey] != false }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[dynamicColorKey] = enabled }
    }

    suspend fun setCrashReports(enabled: Boolean) {
        context.dataStore.edit { it[crashReportsKey] = enabled }
    }

    val notificationExplainerShown: Flow<Boolean> = context.dataStore.data.map { it[explainerShown] == true }

    suspend fun hasShownNotificationExplainer(): Boolean = notificationExplainerShown.first()

    suspend fun markNotificationExplainerShown() {
        context.dataStore.edit { it[explainerShown] = true }
    }
}
