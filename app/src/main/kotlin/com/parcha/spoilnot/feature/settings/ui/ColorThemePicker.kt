package com.parcha.spoilnot.feature.settings.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
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
 * light or dark mode, in a grid of up to four (fewer as the text grows, so names wrap between words). The choice is
 * marked with a check and a ring, and read as a selected radio button, so it never relies on color (DESIGN 6.4).
 */
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
        ChoiceLabel(stringResource(R.string.settings_colors))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // A cell grows with the text, so the longest word of a name still fits on its line.
            val cell = SwatchCell * LocalDensity.current.fontScale.coerceIn(1f, 2f)
            val columns = (maxWidth / cell).toInt().coerceIn(1, 4)
            Column(
                Modifier.selectableGroup().testTag("color_themes"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ColorTheme.offered.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { theme ->
                            Swatch(theme, theme == current, dark, Modifier.weight(1f)) { onPick(theme) }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.settings_colors_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The least width of a swatch with its name, at 1.0× text. */
private val SwatchCell = 76.dp

@Composable
private fun Swatch(theme: ColorTheme, selected: Boolean, dark: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = colorSchemeFor(theme, dark)
    val ring = MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(top = 6.dp, bottom = 6.dp, start = 2.dp, end = 2.dp)
            .testTag("color_theme_${theme.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // A 56 dp swatch; the chosen one has a 3 dp ring outside a 3 dp gap.
        Box(
            Modifier
                .size(68.dp)
                .then(if (selected) Modifier.border(3.dp, ring, CircleShape) else Modifier)
                .padding(6.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(56.dp)) {
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
                // On its own dot in the page's text color, so it shows on every part of every swatch.
                selected -> Box(
                    Modifier.size(26.dp).background(ring, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(18.dp),
                    )
                }

                theme == ColorTheme.Wallpaper ->
                    Icon(Icons.Rounded.Wallpaper, contentDescription = null, tint = scheme.onPrimary)
            }
        }
        Text(
            theme.label(),
            style = SpoilNotText.ChipLabel,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // Every label takes two lines, so the swatches in a row line up.
            minLines = 2,
            maxLines = 2,
        )
    }
}
