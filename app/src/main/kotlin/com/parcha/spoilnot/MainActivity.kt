package com.parcha.spoilnot

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.core.firebase.RequestEmulatorNetworkAccess
import com.parcha.spoilnot.core.navigation.DeepLink
import com.parcha.spoilnot.core.navigation.DeepLinkRouter
import com.parcha.spoilnot.core.navigation.SpoilNotRoot
import com.parcha.spoilnot.core.prefs.AppPreferences
import com.parcha.spoilnot.core.prefs.ThemeMode
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.recipes.domain.SourceUrl
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var deepLinks: DeepLinkRouter

    @Inject lateinit var preferences: AppPreferences

    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash stays until the session is known (usually from the local cache in a few hundred
        // milliseconds), so the app does not flash a spinner; never longer than SPLASH_MAX_MS.
        val start = SystemClock.uptimeMillis()
        installSplashScreen().setKeepOnScreenCondition {
            sessionManager.session.value == Session.Loading && SystemClock.uptimeMillis() - start < SPLASH_MAX_MS
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A tapped digest opens the app with the FCM data as extras (DESIGN 10.2); a share brings a link (11.1).
        if (savedInstanceState == null) deepLinks.open(linkFrom(intent))
        setContent {
            val themeMode by preferences.themeMode.collectAsStateWithLifecycle(ThemeMode.System)
            val dynamicColor by preferences.dynamicColor.collectAsStateWithLifecycle(true)
            val dark = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            SpoilNotTheme(darkTheme = dark, dynamicColor = dynamicColor) {
                RequestEmulatorNetworkAccess()
                SpoilNotRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinks.open(linkFrom(intent))
    }

    private fun linkFrom(intent: Intent): DeepLink? = if (intent.action == Intent.ACTION_SEND) {
        DeepLink.ImportRecipe(SourceUrl.fromSharedText(intent.getStringExtra(Intent.EXTRA_TEXT)))
    } else {
        DeepLink.parse(intent.getStringExtra(DeepLink.EXTRA_ROUTE))
    }

    private companion object {
        const val SPLASH_MAX_MS = 1_500L
    }
}
