package com.parcha.spoilnot.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
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
    private val tourDoneKey = booleanPreferencesKey("tour_done")
    private val themeModeKey = stringPreferencesKey("theme_mode")

    /** Before the preset themes: the Material You switch. Read only, to keep an older choice. */
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val crashReportsKey = booleanPreferencesKey("crash_reports")

    private val colorThemeKey = stringPreferencesKey("color_theme")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[themeModeKey] } ?: ThemeMode.System
    }

    /**
     * Settings > Appearance > Colors (DESIGN 6.4), Wallpaper by default. Phones from before the presets keep their
     * choice: the old "Colors from your wallpaper" switch turned off means the Spoil Not green.
     */
    val colorTheme: Flow<ColorTheme> = context.dataStore.data.map { prefs ->
        ColorTheme.fromId(prefs[colorThemeKey])
            ?: if (prefs[dynamicColorKey] == false) ColorTheme.SpoilNot else ColorTheme.Wallpaper
    }

    /** Crash reports, on by default with an off switch (DESIGN 2 telemetry). */
    val crashReports: Flow<Boolean> = context.dataStore.data.map { it[crashReportsKey] != false }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setColorTheme(theme: ColorTheme) {
        context.dataStore.edit { it[colorThemeKey] = theme.id }
    }

    suspend fun setCrashReports(enabled: Boolean) {
        context.dataStore.edit { it[crashReportsKey] = enabled }
    }

    val notificationExplainerShown: Flow<Boolean> = context.dataStore.data.map { it[explainerShown] == true }

    suspend fun hasShownNotificationExplainer(): Boolean = notificationExplainerShown.first()

    suspend fun markNotificationExplainerShown() {
        context.dataStore.edit { it[explainerShown] = true }
    }

    /** The app tour (DESIGN 5.8) was finished or skipped on this phone; false starts it on the next Home. */
    val tourDone: Flow<Boolean> = context.dataStore.data.map { it[tourDoneKey] == true }

    suspend fun setTourDone(done: Boolean) {
        context.dataStore.edit { it[tourDoneKey] = done }
    }
}
