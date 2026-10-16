package com.parcha.spoilnot.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.parcha.spoilnot.feature.auth.ui.AuthFlow
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.ui.HouseholdGateScreen

/** Chooses the top-level screen from the session: sign-in, the household gate, or the app. */
@Composable
fun SpoilNotRoot(modifier: Modifier = Modifier, viewModel: RootViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        when (val s = session) {
            Session.Loading -> Loading()

            Session.SignedOut -> AuthFlow()

            is Session.NoHousehold -> key(s.user.uid) { HouseholdGateScreen(s) }

            // Keyed so nothing from a previous household or account survives (DESIGN 9.1).
            is Session.InHousehold -> key(s.user.uid, s.householdId) { AppShell(s) }
        }
    }
}

@Composable
private fun Loading() {
    val description = stringResource(R.string.loading)
    Box(Modifier.fillMaxSize().testTag("screen_loading"), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = description })
    }
}
