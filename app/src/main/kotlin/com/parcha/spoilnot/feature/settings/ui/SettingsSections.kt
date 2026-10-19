package com.parcha.spoilnot.feature.settings.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.Segmented
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.prefs.ThemeMode
import com.parcha.spoilnot.feature.auth.domain.Units

private val transparent @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
}

/** System, Light or Dark; the color theme (DESIGN 6.4); metric or imperial (DESIGN 5.6). */
@Composable
fun AppearanceCard(
    device: SettingsViewModel.Device,
    units: Units,
    onThemeMode: (ThemeMode) -> Unit,
    onColorTheme: (ColorTheme) -> Unit,
    onUnits: (Units) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("card_appearance")) {
        CardTitle(stringResource(R.string.settings_appearance))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Segmented(
                label = stringResource(R.string.settings_theme),
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
            )
            ColorThemePicker(device.colorTheme, onColorTheme)
            Segmented(
                label = stringResource(R.string.settings_units),
                options = listOf(Units.Metric, Units.Imperial),
                selected = units,
                optionLabel = {
                    stringResource(
                        if (it ==
                            Units.Metric
                        ) {
                            R.string.units_metric
                        } else {
                            R.string.units_imperial
                        },
                    )
                },
                onPick = onUnits,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = icon,
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = transparent,
        modifier = modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange),
    )
}

/** Export household data as JSON (DESIGN 13.4). */
@Composable
fun DataCard(busy: Boolean, onExport: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.settings_data))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_export)) },
            supportingContent = { Text(stringResource(R.string.settings_export_summary)) },
            leadingContent = { Icon(Icons.Rounded.Download, contentDescription = null) },
            colors = transparent,
        )
        OutlinedButton(
            onClick = onExport,
            enabled = !busy,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).heightIn(min = 48.dp)
                .testTag("button_export"),
        ) { Text(stringResource(R.string.action_export)) }
    }
}

/** Crash reports, with an off switch (DESIGN 2, 7.5), the app tour (after v1, 5.8) and About. */
@Composable
fun PrivacyAndAboutCard(
    crashReports: Boolean,
    onCrashReports: (Boolean) -> Unit,
    onOpenAbout: () -> Unit,
    onTakeTour: () -> Unit = {},
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        SwitchRow(
            title = stringResource(R.string.settings_crash_reports),
            summary = stringResource(R.string.settings_crash_reports_summary),
            checked = crashReports,
            onChange = onCrashReports,
            icon = { Icon(Icons.Rounded.BugReport, contentDescription = null) },
            modifier = Modifier.testTag("row_crash_reports"),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_take_tour)) },
            supportingContent = { Text(stringResource(R.string.settings_take_tour_summary)) },
            leadingContent = { Icon(Icons.Rounded.School, contentDescription = null) },
            colors = transparent,
            modifier = Modifier.toggleable(false, role = Role.Button) { onTakeTour() }.testTag("row_take_tour"),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_about)) },
            supportingContent = { Text(stringResource(R.string.settings_about_summary)) },
            leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
            colors = transparent,
            modifier = Modifier.toggleable(false, role = Role.Button) { onOpenAbout() }.testTag("row_about"),
        )
    }
}
