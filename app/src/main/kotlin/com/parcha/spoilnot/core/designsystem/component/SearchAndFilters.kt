package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText

/**
 * A search field on a page (DESIGN 19.10): 56 dp, fully round, on the `row` color without an outline, a search icon
 * at the start and a clear button ([clearLabel]) once something is typed. [placeholder] names it for screen readers
 * too, as Material's placeholder does.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
) {
    PillField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        leadingIcon = Icons.Rounded.Search,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier,
    ) {
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) {
                Icon(
                    Icons.Rounded.Clear,
                    contentDescription = clearLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Keeps the height the clear button gives, so the field doesn't move when it appears.
            Box(Modifier.size(48.dp))
        }
    }
}

/**
 * The round field of a page (DESIGN 19.10), under the search field and the quick-add field: 56 dp, fully round, on the
 * `row` color without an outline, a 2 dp `primary` border while it has the keyboard, an optional [leadingIcon], and
 * [trailing] at the end. [placeholder] names it for screen readers too, as Material's placeholder does.
 */
@Composable
fun PillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyLarge.merge(TextStyle(fontWeight = FontWeight.Medium))
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        singleLine = true,
        textStyle = style.merge(TextStyle(color = colors.onSurface)),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactions,
        decorationBox = { inner ->
            Row(
                Modifier
                    .heightIn(min = FieldMinHeight)
                    .background(SpoilNotSurfaces.current.row, CircleShape)
                    .border(FieldBorderWidth, if (focused) colors.primary else Color.Transparent, CircleShape)
                    .padding(start = 20.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingIcon?.let { Icon(it, contentDescription = null, tint = colors.onSurfaceVariant) }
                Box(
                    Modifier
                        .weight(1f)
                        .padding(start = if (leadingIcon != null) 12.dp else 0.dp, end = 12.dp)
                        .padding(vertical = 14.dp),
                ) {
                    if (value.isEmpty()) Text(placeholder, style = style, color = colors.onSurfaceVariant, maxLines = 1)
                    inner()
                }
                trailing()
            }
        },
    )
}

/**
 * A filter chip of the 2026-11 look (DESIGN 19.10): 44 dp, on the `row` color with 12 dp corners and an optional
 * [icon]; chosen, it is fully round, `primary` with a check. Material's chip underneath, so screen readers hear it as
 * a checkbox that is selected or not. In a dialog pass the `dialogRow` [color], as its choices use.
 */
@Composable
fun SpoilNotFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = SpoilNotSurfaces.current.row,
) {
    val colors = MaterialTheme.colorScheme
    val leading = if (selected) Icons.Rounded.Check else icon
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text,
                style = SpoilNotText.ButtonMedium,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                maxLines = 1,
            )
        },
        leadingIcon = leading?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        modifier = modifier.heightIn(min = 44.dp),
        shape = if (selected) CircleShape else RoundedCornerShape(SpoilNotCorners.FilterChip),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = color,
            labelColor = colors.onSurfaceVariant,
            iconColor = colors.onSurfaceVariant,
            selectedContainerColor = colors.primary,
            selectedLabelColor = colors.onPrimary,
            selectedLeadingIconColor = colors.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
        ),
    )
}
