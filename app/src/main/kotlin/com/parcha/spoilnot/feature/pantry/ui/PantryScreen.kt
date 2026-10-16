package com.parcha.spoilnot.feature.pantry.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.EmptyState
import com.parcha.spoilnot.core.designsystem.component.TabScaffold

@Composable
fun PantryScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    TabScaffold(
        title = stringResource(R.string.tab_pantry),
        onOpenSettings = onOpenSettings,
        modifier = modifier.testTag("screen_pantry"),
    ) { padding ->
        EmptyState(
            icon = Icons.Outlined.Kitchen,
            message = stringResource(R.string.empty_pantry),
            modifier = Modifier.padding(padding),
        )
    }
}
