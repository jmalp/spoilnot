package com.parcha.spoilnot.feature.settings.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.colorSchemeFor

/** The theme's name in Settings. */
@Composable
fun ColorTheme.label(): String = stringResource(
    when (this) {
        ColorTheme.Wallpaper -> R.string.colors_wallpaper
        ColorTheme.SpoilNot -> R.string.colors_spoilnot
        ColorTheme.Fiery -> R.string.colors_fiery
        ColorTheme.SummerFun -> R.string.colors_summer_fun
        ColorTheme.EnchantedForest -> R.string.colors_enchanted_forest
        ColorTheme.WatermelonSorbet -> R.string.colors_watermelon_sorbet
        ColorTheme.ContrastPop -> R.string.colors_contrast_pop
    },
)

/**
 * Settings > Appearance > Colors: one swatch per theme, showing its main, second and accent colors in the current
 * light or dark mode. The choice is marked with a check and a ring, and read as a selected radio button, so it never
 * relies on color (DESIGN 6.4).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorThemePicker(selected: ColorTheme, onPick: (ColorTheme) -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // Without Material You, a stored Wallpaper choice shows as the Spoil Not green it falls back to.
    val current = if (selected == ColorTheme.Wallpaper &&
        !ColorTheme.wallpaperAvailable
    ) {
        ColorTheme.SpoilNot
    } else {
        selected
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_colors), style = MaterialTheme.typography.labelLarge)
        FlowRow(
            Modifier.selectableGroup().testTag("color_themes"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorTheme.offered.forEach { theme -> Swatch(theme, theme == current, dark) { onPick(theme) } }
        }
        Text(
            stringResource(R.string.settings_colors_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Swatch(theme: ColorTheme, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
    val scheme = colorSchemeFor(theme, dark)
    val ring = MaterialTheme.colorScheme.onSurface
    // Wider with large text, so names wrap between words instead of inside them.
    val width = 76.dp * LocalDensity.current.fontScale.coerceIn(1f, 2f)
    Column(
        Modifier
            .width(width)
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag("color_theme_${theme.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .then(if (selected) Modifier.border(3.dp, ring, CircleShape) else Modifier)
                .padding(5.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(42.dp)) {
                // Top half the main color; the bottom split between the second and accent colors.
                drawRect(scheme.primary, size = Size(size.width, size.height / 2))
                drawRect(scheme.secondaryContainer, Offset(0f, size.height / 2), Size(size.width / 2, size.height / 2))
                drawRect(
                    scheme.tertiary,
                    Offset(size.width / 2, size.height / 2),
                    Size(size.width / 2, size.height / 2),
                )
            }
            when {
                // On its own dot, so it shows on every part of the swatch (after v1; it vanished on light quarters).
                selected -> Box(
                    Modifier.size(
                        24.dp,
                    ).background(scheme.primary, CircleShape).border(1.5.dp, scheme.onPrimary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }

                theme == ColorTheme.Wallpaper ->
                    Icon(Icons.Rounded.Wallpaper, contentDescription = null, tint = scheme.onPrimary)
            }
        }
        Text(
            theme.label(),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            // Every label takes two lines, so the swatches in a row line up.
            minLines = 2,
            maxLines = 2,
        )
    }
}
