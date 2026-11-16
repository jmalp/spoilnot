package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText

/** The least height of a text field's box, and of a picker that looks like one. */
val FieldMinHeight = 56.dp

/** How thick a focused or wrong field's border is. */
val FieldBorderWidth = 2.dp

/** A field's label, in [FieldLabel]'s color for its state: `error`, `primary` while focused, else `onSurfaceVariant`. */
@Composable
fun fieldLabelColor(focused: Boolean, isError: Boolean): Color = when {
    isError -> MaterialTheme.colorScheme.error
    focused -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** The label above a field or a picker (DESIGN 19.10). */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = SpoilNotText.FieldLabel, color = color, modifier = modifier)
}

/**
 * A text field of the 2026-11 look (DESIGN 19.10): [label] above a box at least 56 dp tall with 16 dp corners on the
 * `field` color, no outline at rest, a 2 dp `primary` border while focused and a 2 dp `error` border with an icon and
 * the message under it when [error] is set; otherwise [helper] goes under it.
 *
 * Material's text fields put the label inside the box, and only its `TextFieldState` version can put it above, so
 * this is a `BasicTextField` with its own decoration. The label and the message are part of the field's decoration,
 * so screen readers read them with the field, as they do Material's; buttons in [leadingIcon] or [trailingIcon] stay
 * their own stops.
 */
@Composable
fun SpoilNotTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    error: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    textStyle: TextStyle = SpoilNotText.FieldText,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val isError = error != null
    val textColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = DISABLED_ALPHA)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        // One node for screen readers: the label, what is typed and the message, as Material's fields read.
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .errorSemantics(error),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle.merge(TextStyle(color = textColor)),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        interactionSource = interactions,
        cursorBrush = SolidColor(if (isError) colors.error else colors.primary),
        decorationBox = { inner ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(label, color = fieldLabelColor(focused, isError))
                FieldBox(
                    focused = focused,
                    isError = isError,
                    enabled = enabled,
                    startPadding = if (leadingIcon != null) 12.dp else 16.dp,
                    endPadding = if (trailingIcon != null) 4.dp else 16.dp,
                ) {
                    CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) {
                        leadingIcon?.invoke()
                        Box(Modifier.weight(1f).padding(vertical = 14.dp), contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(placeholder, style = textStyle, color = colors.onSurfaceVariant, maxLines = 1)
                            }
                            inner()
                        }
                        trailingIcon?.invoke()
                    }
                }
                FieldMessage(error = error, helper = helper)
            }
        },
    )
}

/**
 * The box of a field or of a picker that looks like one: the `field` color, 16 dp corners ([shape]), at least 56 dp
 * tall, and the 2 dp border while [focused] or in error.
 */
@Composable
fun FieldBox(
    focused: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(SpoilNotCorners.Field),
    startPadding: Dp = 16.dp,
    endPadding: Dp = 16.dp,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val border = when {
        isError -> colors.error
        focused -> colors.primary
        else -> Color.Transparent
    }
    val background = SpoilNotSurfaces.current.field.let { if (enabled) it else it.copy(alpha = DISABLED_FIELD_ALPHA) }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = FieldMinHeight)
            .background(background, shape)
            .border(FieldBorderWidth, border, shape)
            .padding(start = startPadding, end = endPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** The line under a field: the error with an icon, in `error` (so it never relies on color), or the helper. */
@Composable
fun FieldMessage(error: String?, helper: String?, modifier: Modifier = Modifier) {
    when {
        error != null -> Row(
            modifier.padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 1.dp).size(16.dp),
            )
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        helper != null -> Text(
            helper,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(horizontal = 4.dp),
        )
    }
}

/** Says a field is wrong, with [message], as Material's fields do; nothing when [message] is null. */
fun Modifier.errorSemantics(message: String?): Modifier = if (message == null) this else semantics { error(message) }

/** Text and icons of a field that can't be changed, as Material dims them. */
private const val DISABLED_ALPHA = 0.38f

/** A field that can't be changed keeps a hint of its box. */
private const val DISABLED_FIELD_ALPHA = 0.5f
