package com.parcha.spoilnot.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.QuotaBanner
import com.parcha.spoilnot.feature.auth.ui.AuthFlow
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.ui.HouseholdGateScreen
import com.parcha.spoilnot.feature.update.ui.UpdateRequiredScreen

/** Chooses the top-level screen from the session: sign-in, the household gate, or the app. */
@Composable
fun SpoilNotRoot(modifier: Modifier = Modifier, viewModel: RootViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val updateRequired by viewModel.updateRequired.collectAsStateWithLifecycle()
    val quotaReached by viewModel.quotaBanner.collectAsStateWithLifecycle()
    // A Surface sets the text color for every top-level screen, so none falls back to black in dark mode.
    Surface(modifier.fillMaxSize()) {
        RootLayout(quotaReached) {
            val outdated = updateRequired
            if (outdated != null) {
                UpdateRequiredScreen(outdated)
                return@RootLayout
            }
            when (val s = session) {
                Session.Loading -> Loading()

                Session.SignedOut -> AuthFlow()

                is Session.NoHousehold -> key(s.user.uid) { HouseholdGateScreen(s) }

                // Keyed so nothing from a previous household or account survives (DESIGN 9.1).
                is Session.InHousehold -> key(s.user.uid, s.householdId) { AppShell(s) }
            }
        }
    }
}

/**
 * The quota banner (DESIGN 6.7) above whichever top-level screen shows (after v1, REL-02): inside the app it could not
 * show while the session reconnected, and a gate or sign-in screen had none. The banner takes the status bar, so the
 * screen below doesn't pad for it again.
 */
@Composable
internal fun RootLayout(quotaReached: Boolean, content: @Composable BoxScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (quotaReached) QuotaBanner()
        val insets = if (quotaReached) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier
        Box(Modifier.weight(1f).fillMaxWidth().then(insets), content = content)
    }
}

@Composable
private fun Loading() {
    val description = stringResource(R.string.loading)
    Box(Modifier.fillMaxSize().testTag("screen_loading"), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = description })
    }
}
