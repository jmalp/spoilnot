package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText

/**
 * − or + of a stepper (DESIGN 19.9, 19.10): 56 dp, `secondaryContainer`, fully round on the outside and 8 dp where it
 * joins the value. [label] names it for screen readers.
 */
@Composable
fun StepButton(
    icon: ImageVector,
    label: String,
    start: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    size: Dp = FieldMinHeight,
    onClick: () -> Unit,
) {
    val round = CornerSize(percent = 50)
    val joined = CornerSize(SpoilNotCorners.Connected)
    val shape = if (start) {
        RoundedCornerShape(topStart = round, topEnd = joined, bottomEnd = joined, bottomStart = round)
    } else {
        RoundedCornerShape(topStart = joined, topEnd = round, bottomEnd = round, bottomStart = joined)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = MaterialTheme.colorScheme.secondaryContainer.let { if (enabled) it else it.copy(alpha = 0.5f) },
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer.let {
            if (enabled) it else it.copy(alpha = 0.38f)
        },
        modifier = modifier.size(size).semantics { role = Role.Button },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label) }
    }
}

/** The value's least width between − and +, as Add item's amount: room for a few digits. */
private val StepperValueMinWidth = 72.dp

/**
 * A stepper for a count that isn't typed (DESIGN 19.9): − and + joined to the [value] shown between them on the field
 * color, such as how many pieces to open or a meal's servings. [valueTag] tags the value for tests. [size] is the
 * buttons' and the value's height: 56 dp on a sheet, 48 dp beside a recipe's Ingredients heading, with [valueStyle].
 * The value is announced when it changes ([announce]), where nothing else on screen says it.
 */
@Composable
fun CountStepper(
    value: String,
    minusLabel: String,
    plusLabel: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
    valueTag: String? = null,
    size: Dp = FieldMinHeight,
    valueStyle: TextStyle = SpoilNotText.FieldText,
    announce: Boolean = false,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(ListGroupGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(
            Icons.Rounded.Remove,
            minusLabel,
            start = true,
            enabled = minusEnabled,
            size = size,
            onClick = onMinus,
        )
        Box(
            Modifier
                .heightIn(min = size)
                .widthIn(min = StepperValueMinWidth)
                .background(SpoilNotSurfaces.current.field, RoundedCornerShape(SpoilNotCorners.Connected))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                value,
                style = valueStyle,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .then(if (announce) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier)
                    .then(if (valueTag != null) Modifier.testTag(valueTag) else Modifier),
            )
        }
        StepButton(Icons.Rounded.Add, plusLabel, start = false, enabled = plusEnabled, size = size, onClick = onPlus)
    }
}
