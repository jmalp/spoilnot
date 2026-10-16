package com.parcha.spoilnot.feature.settings.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.DetailScaffold
import com.parcha.spoilnot.core.designsystem.component.EmptyState

@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    DetailScaffold(
        title = stringResource(R.string.settings),
        onBack = onBack,
        modifier = modifier.testTag("screen_settings"),
    ) { padding ->
        EmptyState(
            icon = Icons.Outlined.Settings,
            message = stringResource(R.string.empty_settings),
            modifier = Modifier.padding(padding),
        )
    }
}
