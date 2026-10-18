package com.parcha.spoilnot.feature.update.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.feature.update.domain.AppConfig

/** DESIGN 6.7 and 13.3: this build is too old for the household's data. */
@Composable
fun UpdateRequiredScreen(config: AppConfig, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Column(
        modifier.fillMaxSize().padding(32.dp).testTag("screen_update_required"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(48.dp))
        Text(
            stringResource(R.string.update_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            config.message ?: stringResource(R.string.update_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        config.updateUrl?.let { url ->
            PrimaryButton(text = stringResource(R.string.update_action), onClick = { runCatching { uri.openUri(url) } })
        }
    }
}
