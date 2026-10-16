package com.parcha.spoilnot.feature.home.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.EmptyState
import com.parcha.spoilnot.core.designsystem.component.TabScaffold

@Composable
fun HomeScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    TabScaffold(
        title = stringResource(R.string.tab_home),
        onOpenSettings = onOpenSettings,
        modifier = modifier.testTag("screen_home"),
    ) { padding ->
        EmptyState(
            icon = Icons.Outlined.Home,
            message = stringResource(R.string.empty_home),
            modifier = Modifier.padding(padding),
        )
    }
}
