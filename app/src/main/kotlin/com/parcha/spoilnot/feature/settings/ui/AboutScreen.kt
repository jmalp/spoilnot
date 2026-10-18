package com.parcha.spoilnot.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.parcha.spoilnot.BuildConfig
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.DetailScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Settings > About: version, privacy summary and open source licenses (DESIGN 5.6). */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val device by viewModel.device.collectAsStateWithLifecycle()
    // Seven taps on the version offer a test crash, to check that crash reports arrive (M6 beta).
    var versionTaps by rememberSaveable { mutableIntStateOf(0) }
    var confirmCrash by rememberSaveable { mutableStateOf(false) }
    val resources = LocalResources.current
    // Generated at build time by the AboutLibraries plugin from the app's dependencies.
    val libraries by produceLibraries {
        withContext(Dispatchers.IO) {
            resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
        }
    }
    DetailScaffold(
        title = stringResource(R.string.settings_about),
        onBack = onBack,
        modifier = modifier.testTag("screen_about"),
    ) { padding ->
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier.padding(padding).fillMaxSize(),
            header = {
                item {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.clickable(
                                interactionSource = null,
                                indication = null,
                                onClickLabel = null,
                            ) {
                                versionTaps++
                                if (versionTaps >= 7 && device.crashReports) {
                                    versionTaps = 0
                                    confirmCrash = true
                                }
                            },
                        )
                        Text(
                            stringResource(R.string.about_privacy_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(stringResource(R.string.about_privacy_body), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.about_licenses),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                        )
                    }
                }
            },
        )
    }
    if (confirmCrash) {
        AlertDialog(
            onDismissRequest = { confirmCrash = false },
            title = { Text(stringResource(R.string.about_test_crash_title)) },
            text = { Text(stringResource(R.string.about_test_crash_body)) },
            confirmButton = {
                TextButton(onClick = { throw TestCrashException() }) { Text(stringResource(R.string.about_test_crash)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCrash = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Thrown on purpose from About, so Crashlytics shows a recognizable report. */
class TestCrashException : RuntimeException("Test crash from Settings > About")
