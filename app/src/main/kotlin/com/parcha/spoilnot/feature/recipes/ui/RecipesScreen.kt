package com.parcha.spoilnot.feature.recipes.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.EmptyState
import com.parcha.spoilnot.core.designsystem.component.TabScaffold

@Composable
fun RecipesScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    TabScaffold(
        title = stringResource(R.string.tab_recipes),
        onOpenSettings = onOpenSettings,
        modifier = modifier.testTag("screen_recipes"),
    ) { padding ->
        EmptyState(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            message = stringResource(R.string.empty_recipes),
            modifier = Modifier.padding(padding),
        )
    }
}
