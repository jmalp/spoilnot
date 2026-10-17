package com.parcha.spoilnot.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_preferences")

/** Small on-device settings that are not household data (DESIGN 7.3: androidx.datastore). */
@Singleton
class AppPreferences @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val explainerShown = booleanPreferencesKey("notification_explainer_shown")

    val notificationExplainerShown: Flow<Boolean> = context.dataStore.data.map { it[explainerShown] == true }

    suspend fun hasShownNotificationExplainer(): Boolean = notificationExplainerShown.first()

    suspend fun markNotificationExplainerShown() {
        context.dataStore.edit { it[explainerShown] = true }
    }
}
