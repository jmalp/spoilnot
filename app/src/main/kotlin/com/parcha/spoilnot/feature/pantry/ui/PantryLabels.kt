package com.parcha.spoilnot.feature.pantry.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotStatus
import com.parcha.spoilnot.core.designsystem.theme.StatusTone
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import com.parcha.spoilnot.feature.pantry.domain.ExpiryStatus
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Fixed status palette tones (DESIGN 6.4): week counts as fresh. */
fun ExpiryStatus.tone(): StatusTone = when (this) {
    ExpiryStatus.Expired -> StatusTone.Expired
    ExpiryStatus.Today, ExpiryStatus.Tomorrow -> StatusTone.Urgent
    ExpiryStatus.Soon -> StatusTone.Soon
    ExpiryStatus.Week, ExpiryStatus.Fresh -> StatusTone.Fresh
    ExpiryStatus.NoDate -> StatusTone.NoDate
}

fun ExpiryStatus.icon(): ImageVector = when (this) {
    ExpiryStatus.Expired -> Icons.Rounded.DeleteOutline
    ExpiryStatus.Today, ExpiryStatus.Tomorrow -> Icons.Rounded.WarningAmber
    ExpiryStatus.Soon -> Icons.Rounded.Schedule
    ExpiryStatus.Week, ExpiryStatus.Fresh -> Icons.Rounded.CheckCircle
    ExpiryStatus.NoDate -> Icons.Rounded.Remove
}

/** The full wording from the status table (DESIGN 5.2), for batch rows and screen readers. */
@Composable
fun statusText(expiry: LocalDate?, dateType: DateType, today: LocalDate): String {
    val status = ExpiryStatus.of(expiry, today)
    val days = expiry?.let { ExpiryStatus.daysUntil(it, today).toInt() } ?: 0
    val useBy = dateType == DateType.UseBy
    return when (status) {
        ExpiryStatus.Expired -> if (useBy) {
            pluralStringResource(R.plurals.status_expired_use_by, abs(days), abs(days))
        } else {
            stringResource(R.string.status_expired_best_before)
        }

        ExpiryStatus.Today -> stringResource(
            if (useBy) R.string.status_today_use_by else R.string.status_today_best_before,
        )

        ExpiryStatus.Tomorrow ->
            stringResource(if (useBy) R.string.status_tomorrow_use_by else R.string.status_tomorrow_best_before)

        ExpiryStatus.Soon -> pluralStringResource(
            if (useBy) R.plurals.status_soon_use_by else R.plurals.status_soon_best_before,
            days,
            days,
        )

        ExpiryStatus.Week -> pluralStringResource(R.plurals.status_week, days, days)

        ExpiryStatus.Fresh -> formatDate(expiry!!, today)

        ExpiryStatus.NoDate -> stringResource(R.string.status_no_date)
    }
}

/** Short text for the chip on a list row. */
@Composable
fun chipText(expiry: LocalDate?, dateType: DateType, today: LocalDate): String {
    val status = ExpiryStatus.of(expiry, today)
    val days = expiry?.let { ExpiryStatus.daysUntil(it, today).toInt() } ?: 0
    return when (status) {
        ExpiryStatus.Expired ->
            stringResource(if (dateType == DateType.UseBy) R.string.chip_expired else R.string.chip_past_best_before)

        ExpiryStatus.Today -> stringResource(R.string.chip_today)

        ExpiryStatus.Tomorrow -> stringResource(R.string.chip_tomorrow)

        ExpiryStatus.Soon, ExpiryStatus.Week -> pluralStringResource(R.plurals.chip_days, days, days)

        ExpiryStatus.Fresh -> formatDate(expiry!!, today)

        ExpiryStatus.NoDate -> stringResource(R.string.status_no_date)
    }
}

/** "Oct 20", or "Mar 2027" for a date in another year. */
fun formatDate(date: LocalDate, today: LocalDate): String = date.format(
    if (date.year == today.year) DateTimeFormatter.ofPattern("MMM d") else DateTimeFormatter.ofPattern("MMM yyyy"),
)

/**
 * Status chip: icon, text and the fixed status color, never color alone (DESIGN 6.4). Screen readers get the
 * full wording.
 */
@Composable
fun StatusChip(expiry: LocalDate?, dateType: DateType, today: LocalDate, modifier: Modifier = Modifier) {
    val status = ExpiryStatus.of(expiry, today)
    val color = SpoilNotStatus.colors.forTone(status.tone())
    val description = statusText(expiry, dateType, today)
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = description }
            .testTag("status_chip"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(status.icon(), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(chipText(expiry, dateType, today), style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@StringRes
fun Location.label(): Int = when (this) {
    Location.Fridge -> R.string.location_fridge
    Location.Freezer -> R.string.location_freezer
    Location.Pantry -> R.string.location_pantry
    Location.Other -> R.string.location_other
}

fun Location.icon(): ImageVector = when (this) {
    Location.Fridge -> Icons.Outlined.Kitchen
    Location.Freezer -> Icons.Outlined.AcUnit
    Location.Pantry -> Icons.Outlined.Inventory2
    Location.Other -> Icons.Outlined.Place
}

@StringRes
fun Category.label(): Int = when (this) {
    Category.Produce -> R.string.category_produce
    Category.Dairy -> R.string.category_dairy
    Category.MeatFish -> R.string.category_meat_fish
    Category.Bakery -> R.string.category_bakery
    Category.Frozen -> R.string.category_frozen
    Category.PantryStaples -> R.string.category_pantry_staples
    Category.Drinks -> R.string.category_drinks
    Category.Snacks -> R.string.category_snacks
    Category.Other -> R.string.category_other
}

@StringRes
fun QuantityUnit.label(): Int = when (this) {
    QuantityUnit.Pieces -> R.string.unit_pcs
    QuantityUnit.Grams -> R.string.unit_g
    QuantityUnit.Kilograms -> R.string.unit_kg
    QuantityUnit.Ounces -> R.string.unit_oz
    QuantityUnit.Pounds -> R.string.unit_lb
    QuantityUnit.Milliliters -> R.string.unit_ml
    QuantityUnit.Liters -> R.string.unit_l
    QuantityUnit.FluidOunces -> R.string.unit_fl_oz
}

@StringRes
fun DateType.label(): Int = if (this == DateType.UseBy) R.string.date_type_use_by else R.string.date_type_best_before

@StringRes
fun DiscardReason.label(): Int = when (this) {
    DiscardReason.Expired -> R.string.reason_expired
    DiscardReason.Spoiled -> R.string.reason_spoiled
    DiscardReason.DidNotLike -> R.string.reason_didnt_like
    DiscardReason.Other -> R.string.reason_other
}

private val QUANTITY = DecimalFormat("0.##")

/** 2, 1.5 or 0.25: no trailing zeros. */
fun formatQuantity(value: Double): String = QUANTITY.format(value)

/** Small pill such as Out or Low. */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier
            .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
