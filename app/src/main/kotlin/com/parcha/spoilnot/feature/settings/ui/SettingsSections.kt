package com.parcha.spoilnot.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.ActionRow
import com.parcha.spoilnot.core.designsystem.component.ConnectedButtonGroup
import com.parcha.spoilnot.core.designsystem.component.GroupHeading
import com.parcha.spoilnot.core.designsystem.component.ListGroupGap
import com.parcha.spoilnot.core.designsystem.component.PlainCard
import com.parcha.spoilnot.core.designsystem.component.SegmentPosition
import com.parcha.spoilnot.core.designsystem.component.SwitchRow
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.core.prefs.ThemeMode
import com.parcha.spoilnot.feature.auth.domain.Units

/** A choice's label on the Appearance card: the field label's style, in `onSurfaceVariant`. */
@Composable
internal fun ChoiceLabel(text: String) {
    Text(text, style = SpoilNotText.FieldLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Appearance (DESIGN 6.4, 5.6, 19.9) under its group heading, on a plain card: System, Light or Dark and metric or
 * imperial as connected button groups (a list of radio buttons from 1.5× text), and the color themes.
 */
@Composable
fun AppearanceCard(
    device: SettingsViewModel.Device,
    units: Units,
    onThemeMode: (ThemeMode) -> Unit,
    onColorTheme: (ColorTheme) -> Unit,
    onUnits: (Units) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GroupHeading(stringResource(R.string.settings_appearance))
        PlainCard(Modifier.testTag("card_appearance"), contentPadding = PaddingValues(16.dp), spacing = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceLabel(stringResource(R.string.settings_theme))
                ConnectedButtonGroup(
                    options = ThemeMode.entries,
                    selected = device.themeMode,
                    optionLabel = {
                        stringResource(
                            when (it) {
                                ThemeMode.System -> R.string.theme_system
                                ThemeMode.Light -> R.string.theme_light
                                ThemeMode.Dark -> R.string.theme_dark
                            },
                        )
                    },
                    onPick = onThemeMode,
                    optionTag = { "theme_mode_${it.name.lowercase()}" },
                )
            }
            ColorThemePicker(device.colorTheme, onColorTheme)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceLabel(stringResource(R.string.settings_units))
                ConnectedButtonGroup(
                    options = listOf(Units.Metric, Units.Imperial),
                    selected = units,
                    optionLabel = {
                        stringResource(if (it == Units.Metric) R.string.units_metric else R.string.units_imperial)
                    },
                    onPick = onUnits,
                    optionTag = { "units_${it.name.lowercase()}" },
                )
            }
        }
    }
}

/**
 * Data (DESIGN 13.4, 2, 7.5) under its group heading: Export household data as a row that starts the export, and Send
 * crash reports with its switch.
 */
@Composable
fun DataSection(busy: Boolean, crashReports: Boolean, onExport: () -> Unit, onCrashReports: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GroupHeading(stringResource(R.string.settings_data))
        Column(verticalArrangement = Arrangement.spacedBy(ListGroupGap)) {
            ActionRow(
                position = SegmentPosition.First,
                icon = Icons.Rounded.Download,
                title = stringResource(R.string.settings_export),
                summary = stringResource(R.string.settings_export_summary),
                onClick = onExport,
                enabled = !busy,
                modifier = Modifier.testTag("button_export"),
            )
            SwitchRow(
                position = SegmentPosition.Last,
                icon = Icons.Rounded.BugReport,
                title = stringResource(R.string.settings_crash_reports),
                summary = stringResource(R.string.settings_crash_reports_summary),
                checked = crashReports,
                onCheckedChange = onCrashReports,
                modifier = Modifier.testTag("row_crash_reports"),
            )
        }
    }
}

/**
 * The app tour (after v1, DESIGN 5.8) and About, one group. Both are plain buttons: toggleable made TalkBack say "not
 * checked" on them (review A11Y-08).
 */
@Composable
fun TourAndAbout(onTakeTour: () -> Unit, onOpenAbout: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ListGroupGap)) {
        ActionRow(
            position = SegmentPosition.First,
            icon = Icons.Rounded.Explore,
            title = stringResource(R.string.settings_take_tour),
            summary = stringResource(R.string.settings_take_tour_summary),
            onClick = onTakeTour,
            modifier = Modifier.testTag("row_take_tour"),
        )
        ActionRow(
            position = SegmentPosition.Last,
            icon = Icons.Rounded.Info,
            title = stringResource(R.string.settings_about),
            summary = stringResource(R.string.settings_about_summary),
            onClick = onOpenAbout,
            opensPage = true,
            modifier = Modifier.testTag("row_about"),
        )
    }
}
