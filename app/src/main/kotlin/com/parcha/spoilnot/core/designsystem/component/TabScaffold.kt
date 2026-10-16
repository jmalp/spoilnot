package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R

/** Content never grows wider than this on tablets and landscape (DESIGN 6.2). */
val MaxContentWidth = 640.dp

/** Scaffold for a main tab: large top app bar with the avatar button that opens Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabScaffold(
    title: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.AccountCircle, contentDescription = stringResource(R.string.open_settings))
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
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
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
