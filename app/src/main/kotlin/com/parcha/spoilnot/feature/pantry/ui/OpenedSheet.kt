package com.parcha.spoilnot.feature.pantry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.component.rememberFormSheet
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import java.time.LocalDate

/** "Use within" choices for Mark opened, in days; a remembered value outside them is offered too. */
internal val OPENED_DAY_CHOICES = listOf(1, 3, 5, 7, 14, 30)

/**
 * Mark opened (DESIGN 5.2, after v1): how many were opened (for a batch kept in pieces) and how long they keep
 * once opened, starting from what was chosen for this product last time. Shows the date it will get, which never
 * moves later than the date it has.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OpenedSheet(
    batch: StockBatch,
    rememberedDays: Int?,
    today: LocalDate,
    onSave: (count: Double, days: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val pieces = batch.unit == QuantityUnit.Pieces && batch.quantity >= 2
    var count by rememberSaveable { mutableIntStateOf(1) }
    var days by rememberSaveable { mutableIntStateOf(rememberedDays ?: 7) }
    val choices = (OPENED_DAY_CHOICES + listOfNotNull(rememberedDays)).distinct().sorted()
    val byDays = today.plusDays(days.toLong())
    val keepsDate = batch.expiryDate != null && batch.expiryDate.isBefore(byDays)
    val sheet = rememberFormSheet()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet.state,
        contentWindowInsets = sheet.contentWindowInsets,
        modifier = sheet.modifier.testTag("sheet_opened"),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.opened_title, batch.productName),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (pieces) {
                Text(stringResource(R.string.opened_how_many), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { count = (count - 1).coerceAtLeast(1) }, enabled = count > 1) {
                        Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.opened_fewer))
                    }
                    Text(
                        stringResource(R.string.opened_count, count, formatQuantity(batch.quantity)),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("opened_count"),
                    )
                    IconButton(
                        onClick = { count = (count + 1).coerceAtMost(batch.quantity.toInt()) },
                        enabled = count < batch.quantity.toInt(),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.opened_more))
                    }
                }
            }
            Text(stringResource(R.string.opened_use_within), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { choice ->
                    FilterChip(
                        selected = days == choice,
                        onClick = { days = choice },
                        label = { Text(daysLabel(choice)) },
                        modifier = Modifier.testTag("opened_days_$choice"),
                    )
                }
            }
            Text(
                if (keepsDate) {
                    stringResource(R.string.opened_keeps_date, formatDate(batch.expiryDate!!, today))
                } else {
                    stringResource(R.string.opened_new_date, formatDate(byDays, today))
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.opened_remembered, batch.productName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                text = stringResource(R.string.action_mark_opened),
                onClick = { onSave(if (pieces) count.toDouble() else batch.quantity, days) },
                modifier = Modifier.testTag("button_mark_opened"),
            )
        }
    }
}

@Composable
private fun daysLabel(days: Int): String = when (days) {
    7 -> stringResource(R.string.opened_one_week)
    14 -> stringResource(R.string.opened_two_weeks)
    30 -> stringResource(R.string.opened_one_month)
    else -> pluralStringResource(R.plurals.opened_days, days, days)
}
