package com.parcha.spoilnot

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.core.firebase.RequestEmulatorNetworkAccess
import com.parcha.spoilnot.core.navigation.DeepLink
import com.parcha.spoilnot.core.navigation.DeepLinkRouter
import com.parcha.spoilnot.core.navigation.SpoilNotRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var deepLinks: DeepLinkRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A tapped digest opens the app with the FCM data as extras (DESIGN 10.2).
        if (savedInstanceState == null) deepLinks.open(DeepLink.parse(intent.getStringExtra(DeepLink.EXTRA_ROUTE)))
        setContent {
            SpoilNotTheme {
                RequestEmulatorNetworkAccess()
                SpoilNotRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinks.open(DeepLink.parse(intent.getStringExtra(DeepLink.EXTRA_ROUTE)))
    }
}
