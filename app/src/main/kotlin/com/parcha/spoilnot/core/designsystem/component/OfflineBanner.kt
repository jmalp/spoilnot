package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import kotlinx.coroutines.delay

/**
 * How long a screen's data must keep coming from the cache before the Offline banner shows (DESIGN 9.5, after v1,
 * 2026-11-21). A screen that starts or opens a new query (Plan's next week) reads the cache first and the server a
 * moment later; without the wait the banner showed for that moment and moved everything under it twice.
 */
const val OFFLINE_BANNER_DELAY_MS = 2_000L

/**
 * The Offline banner for a screen that is [offline]: shown once it has been offline for [OFFLINE_BANNER_DELAY_MS],
 * gone as soon as it isn't. Used where a screen decides whether to show it; [OfflineBanner] without the flag draws it.
 */
@Composable
fun OfflineBanner(offline: Boolean, modifier: Modifier = Modifier) {
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(offline) {
        waited = false
        if (offline) {
            delay(OFFLINE_BANNER_DELAY_MS)
            waited = true
        }
    }
    // Gone in the same frame the server answers, not one effect later.
    if (offline && waited) OfflineBanner(modifier)
}

/**
 * Shown while data comes from the local cache only (DESIGN 9.5): in the 2026-11 look a rounded banner first in the
 * screen's content (DESIGN 19.11), `surfaceContainerHighest` with 16 dp corners. The icon and the words say it.
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .heightIn(min = 48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("offline_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
        Text(
            stringResource(R.string.offline_banner),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
