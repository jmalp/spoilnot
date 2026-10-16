package com.parcha.spoilnot.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.EmptyState
import com.parcha.spoilnot.core.designsystem.component.TabScaffold

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    pendingJoinRequests: Int,
    onReviewJoinRequests: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TabScaffold(
        title = stringResource(R.string.tab_home),
        onOpenSettings = onOpenSettings,
        modifier = modifier.testTag("screen_home"),
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (pendingJoinRequests > 0) {
                JoinRequestsCard(pendingJoinRequests, onReviewJoinRequests, Modifier.padding(16.dp))
            }
            EmptyState(icon = Icons.Outlined.Home, message = stringResource(R.string.empty_home))
        }
    }
}

/** Shown to the owner while someone is waiting for approval (DESIGN 5.6). */
@Composable
private fun JoinRequestsCard(count: Int, onReview: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("card_home_join_requests"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Rounded.GroupAdd, contentDescription = null)
            Text(
                pluralStringResource(R.plurals.home_join_requests, count, count),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = onReview, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_review))
            }
        }
    }
}
