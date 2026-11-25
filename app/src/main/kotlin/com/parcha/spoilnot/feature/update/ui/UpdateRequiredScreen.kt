package com.parcha.spoilnot.feature.update.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.parcha.spoilnot.core.designsystem.component.LeafBadge
import com.parcha.spoilnot.core.designsystem.component.MaxContentWidth
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.feature.update.domain.AppConfig

/**
 * DESIGN 6.7 and 13.3: this build is too old for the household's data. In the 2026-11 look (19): on the page's color,
 * the update icon in a leaf as Welcome has its jar, the title and why, and Update as the main button.
 */
@Composable
fun UpdateRequiredScreen(config: AppConfig, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Surface(modifier.fillMaxSize().testTag("screen_update_required"), color = SpoilNotSurfaces.current.page) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            LeafBadge(
                size = 112.dp,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) { Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(52.dp)) }
            Text(
                stringResource(R.string.update_title),
                style = SpoilNotText.SheetTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                config.message ?: stringResource(R.string.update_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            config.updateUrl?.let { url ->
                PrimaryButton(
                    text = stringResource(R.string.update_action),
                    onClick = { runCatching { uri.openUri(url) } },
                    modifier = Modifier.padding(top = 8.dp).widthIn(max = MaxContentWidth),
                )
            }
        }
    }
}
