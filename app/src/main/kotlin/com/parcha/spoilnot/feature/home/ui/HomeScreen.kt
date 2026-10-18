package com.parcha.spoilnot.feature.home.ui

import android.text.format.DateUtils
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.OfflineBanner
import com.parcha.spoilnot.core.designsystem.component.TabScaffold
import com.parcha.spoilnot.core.designsystem.motion.rememberReduceMotion
import com.parcha.spoilnot.feature.home.domain.ActivityEvent
import com.parcha.spoilnot.feature.home.domain.ActivityType
import com.parcha.spoilnot.feature.home.domain.HomeSummary
import com.parcha.spoilnot.feature.pantry.domain.PantryFilter
import com.parcha.spoilnot.feature.pantry.domain.PantryRow
import com.parcha.spoilnot.feature.pantry.ui.Badge
import com.parcha.spoilnot.feature.pantry.ui.StatusChip
import com.parcha.spoilnot.feature.pantry.ui.label
import java.time.LocalDate

/** Where the Home cards lead. */
class HomeNavigation(
    val openSettings: () -> Unit,
    val openPantry: (PantryFilter) -> Unit,
    val openGroceries: () -> Unit,
    val reviewJoinRequests: () -> Unit,
)

/** How many rows a card lists before "See all". */
private const val CARD_ROWS = 4

@Composable
fun HomeScreen(
    navigation: HomeNavigation,
    pendingJoinRequests: Int,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TabScaffold(
        title = stringResource(R.string.tab_home),
        onOpenSettings = navigation.openSettings,
        modifier = modifier.testTag("screen_home"),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.offline && state.summary != null) OfflineBanner()
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (pendingJoinRequests > 0) JoinRequestsCard(pendingJoinRequests, navigation.reviewJoinRequests)
                val summary = state.summary
                if (summary == null) {
                    repeat(4) { SkeletonCard() }
                } else {
                    HomeCards(summary, state.today, state.myUid, navigation)
                }
            }
        }
    }
}

@Composable
private fun HomeCards(summary: HomeSummary, today: LocalDate, myUid: String, navigation: HomeNavigation) {
    // Expired only: the Expiring soon filter would hide them.
    val attentionFilter =
        if (summary.attention.size == summary.expiredCount) PantryFilter.Expired else PantryFilter.ExpiringSoon
    HomeCard(
        title = stringResource(R.string.home_attention),
        icon = Icons.Rounded.WarningAmber,
        tag = "card_home_attention",
        action = if (summary.attention.isEmpty()) {
            null
        } else {
            pluralStringResource(R.plurals.home_see_all, summary.attention.size, summary.attention.size) to
                { navigation.openPantry(attentionFilter) }
        },
    ) {
        if (summary.attention.isEmpty()) {
            Calm(stringResource(R.string.home_attention_none))
        } else {
            summary.attention.take(CARD_ROWS).forEach { row -> AttentionRow(row, today) }
        }
    }
    HomeCard(
        title = stringResource(R.string.home_low_stock),
        icon = Icons.Rounded.Inventory2,
        tag = "card_home_low_stock",
        action = if (summary.lowStock.isEmpty()) {
            null
        } else {
            pluralStringResource(R.plurals.home_see_all, summary.lowStock.size, summary.lowStock.size) to
                { navigation.openPantry(PantryFilter.LowStock) }
        },
    ) {
        if (summary.lowStock.isEmpty()) {
            Calm(stringResource(R.string.home_low_stock_none))
        } else {
            summary.lowStock.take(CARD_ROWS).forEach { row -> LowStockRow(row) }
        }
    }
    HomeCard(
        title = stringResource(R.string.home_grocery_list),
        icon = Icons.AutoMirrored.Rounded.ListAlt,
        tag = "card_home_groceries",
        action = stringResource(R.string.home_open_list) to navigation.openGroceries,
    ) {
        Text(
            if (summary.groceryCount == 0) {
                stringResource(R.string.home_grocery_empty)
            } else {
                pluralStringResource(R.plurals.home_grocery_count, summary.groceryCount, summary.groceryCount)
            },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    HomeCard(
        title = stringResource(R.string.home_recent_activity),
        icon = Icons.Rounded.History,
        tag = "card_home_activity",
    ) {
        if (summary.recent.isEmpty()) {
            Calm(stringResource(R.string.home_activity_none))
        } else {
            summary.recent.forEach { ActivityLine(it, myUid) }
        }
    }
}

@Composable
private fun HomeCard(
    title: String,
    icon: ImageVector,
    tag: String,
    action: Pair<String, () -> Unit>? = null,
    content: @Composable () -> Unit,
) {
    // Outlined, with solid buttons: tonal buttons on tonal cards were hard to see in light mode (beta feedback).
    OutlinedCard(modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp).weight(1f).semantics { heading() },
                )
            }
            content()
            action?.let { (label, onClick) ->
                Button(
                    onClick = onClick,
                    modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun AttentionRow(row: PantryRow, today: LocalDate) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            row.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        row.soonest?.let { StatusChip(it.expiryDate, it.dateType, today) }
    }
}

@Composable
private fun LowStockRow(row: PantryRow) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            row.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Badge(stringResource(if (row.isOut) R.string.badge_out else R.string.badge_low))
    }
}

@Composable
private fun Calm(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Sam added Eggs to Groceries · 5 min ago" (DESIGN 5.6). */
@Composable
private fun ActivityLine(event: ActivityEvent, myUid: String) {
    val resources = LocalResources.current
    val who = if (event.actorUid == myUid) stringResource(R.string.activity_you) else event.actorName
    val name = event.name.orEmpty()
    val text = when (event.type) {
        ActivityType.StockAdded -> stringResource(R.string.activity_stock_added, who, name)

        ActivityType.GroceryAdded -> when {
            event.count != null -> pluralStringResource(
                R.plurals.activity_grocery_added_from_recipe,
                event.count,
                who,
                event.count,
                name,
                event.listName ?: stringResource(R.string.activity_the_list),
            )

            else -> stringResource(
                R.string.activity_grocery_added,
                who,
                name,
                event.listName ?: stringResource(R.string.activity_the_list),
            )
        }

        ActivityType.GroceryChecked -> stringResource(R.string.activity_grocery_checked, who, name)

        ActivityType.RecipeSaved -> stringResource(R.string.activity_recipe_saved, who, name)

        ActivityType.MemberJoined -> stringResource(R.string.activity_member_joined, who)

        ActivityType.Discarded -> event.reason?.let {
            stringResource(R.string.activity_discarded_reason, who, name, stringResource(it.label()).lowercase())
        } ?: stringResource(R.string.activity_discarded, who, name)
    }
    val time = event.createdAt?.let {
        val now = System.currentTimeMillis()
        if (now - it.toEpochMilli() < DateUtils.MINUTE_IN_MILLIS) {
            resources.getString(R.string.activity_just_now)
        } else {
            DateUtils.getRelativeTimeSpanString(it.toEpochMilli(), now, DateUtils.MINUTE_IN_MILLIS).toString()
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        time?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Shown on the first load only (DESIGN 6.7); still when reduce motion is on. */
@Composable
private fun SkeletonCard() {
    val reduceMotion = rememberReduceMotion()
    val alpha = if (reduceMotion) {
        0.6f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
            label = "skeleton alpha",
        ).value
    }
    OutlinedCard(Modifier.fillMaxWidth().testTag("home_skeleton")) {
        Column(Modifier.padding(16.dp).alpha(alpha), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.fillMaxWidth(
                    0.4f,
                ).height(20.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
            )
            Box(
                Modifier.fillMaxWidth(
                    0.9f,
                ).height(16.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
            )
            Box(
                Modifier.fillMaxWidth(
                    0.7f,
                ).height(16.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
            )
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
            Button(onClick = onReview, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_review))
            }
        }
    }
}
