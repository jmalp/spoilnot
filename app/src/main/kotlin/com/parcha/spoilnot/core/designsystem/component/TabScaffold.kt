package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.navigation.LocalJoinRequestCount

/** Content never grows wider than this on tablets and landscape (DESIGN 6.2). */
val MaxContentWidth = 640.dp

/**
 * Scaffold for a main tab: a compact top app bar (after v1; the large one took a fifth of the screen) with the avatar
 * button that opens Settings. [titleContent] replaces the plain title, for the grocery list switcher.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabScaffold(
    title: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    // Pinned: the bar stays and takes the scrolled-under color, so the title never jumps.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = {
                    titleContent?.invoke() ?: Text(title, style = MaterialTheme.typography.headlineSmall)
                },
                actions = {
                    actions()
                    val requests = LocalJoinRequestCount.current
                    val description = if (requests > 0) {
                        pluralStringResource(R.plurals.open_settings_with_requests, requests, requests)
                    } else {
                        stringResource(R.string.open_settings)
                    }
                    // The tour's last step points here (DESIGN 5.8); the key matches TourTarget.Settings.
                    IconButton(onClick = onOpenSettings, modifier = Modifier.coachMark("tour_settings")) {
                        BadgedBox(badge = { if (requests > 0) Badge { Text(requests.toString()) } }) {
                            Icon(Icons.Rounded.AccountCircle, contentDescription = description)
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding -> CenteredContent(padding, content) }
}

/** Scaffold for a pushed screen: small top app bar with a back button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = actions,
            )
        },
    ) { padding -> CenteredContent(padding, content) }
}

@Composable
private fun CenteredContent(padding: PaddingValues, content: @Composable (PaddingValues) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = MaxContentWidth).fillMaxSize()) { content(padding) }
    }
}
