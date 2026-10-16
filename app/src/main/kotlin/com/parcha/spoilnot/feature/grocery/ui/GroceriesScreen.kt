package com.parcha.spoilnot.feature.grocery.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.EmptyState
import com.parcha.spoilnot.core.designsystem.component.TabScaffold

@Composable
fun GroceriesScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    TabScaffold(
        title = stringResource(R.string.tab_groceries),
        onOpenSettings = onOpenSettings,
        modifier = modifier.testTag("screen_groceries"),
    ) { padding ->
        EmptyState(
            icon = Icons.Outlined.ShoppingCart,
            message = stringResource(R.string.empty_groceries),
            modifier = Modifier.padding(padding),
        )
    }
}
