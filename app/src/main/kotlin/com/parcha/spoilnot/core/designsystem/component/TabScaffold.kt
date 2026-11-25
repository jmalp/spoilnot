package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.navigation.LocalJoinRequestCount

/** Content never grows wider than this on tablets and landscape (DESIGN 6.2). */
val MaxContentWidth = 640.dp

/** The least room under a list's last row: a floating button at 1.0× text with its margins. */
private val MinListBottomPadding = 96.dp

/** Space left above a floating button beyond its own height and Material's 16 dp margin under it. */
private val FloatingButtonGap = 24.dp

/** How far the join-request badge's number grows with the phone's text size; the badge itself doesn't grow. */
private const val BADGE_MAX_FONT_SCALE = 1.2f

/** The bottom padding a screen's list needs, provided by [TabScaffold]; see [listBottomPadding]. */
val LocalListBottomPadding = compositionLocalOf { MinListBottomPadding }

/**
 * Bottom padding for a list on a tab (DESIGN 6.8, after v1, review UX-14): the floating button's measured height
 * plus its margins, at least 96 dp, so the last row (and its ⋮) can scroll clear of the button at any text size.
 */
@Composable
@ReadOnlyComposable
fun listBottomPadding(): Dp = LocalListBottomPadding.current

/**
 * Scaffold for a main tab in the 2026-11 look (DESIGN 19.7): the tinted page, and a pinned [ScreenHeader] with the
 * [eyebrow] line over the [title], the screen's [actions] and the button that opens Settings. It is the height of the
 * compact top app bar it replaces (64 dp at 1.0× text). [titleContent] replaces the plain title, for the grocery list
 * switcher.
 */
@Composable
fun TabScaffold(
    title: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    // The floating button grows with the text; lists leave room for what it measures (review UX-14).
    var fabHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val listPadding = max(MinListBottomPadding, with(density) { fabHeight.toDp() } + 16.dp + FloatingButtonGap)
    val page = SpoilNotSurfaces.current.page
    Scaffold(
        modifier = modifier,
        containerColor = page,
        floatingActionButton = {
            Box(Modifier.onSizeChanged { fabHeight = it.height }) { floatingActionButton() }
        },
        snackbarHost = snackbarHost,
        topBar = {
            // Pinned, on the page's color: the header never moves or changes color while the list scrolls under it.
            ScreenHeader(
                title = title,
                eyebrow = eyebrow,
                titleContent = titleContent,
                modifier = Modifier
                    .background(page)
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                    ),
            ) {
                actions()
                SettingsButton(onOpenSettings)
            }
        },
    ) { padding ->
        CompositionLocalProvider(LocalListBottomPadding provides listPadding) { CenteredContent(padding, content) }
    }
}

/**
 * The button that opens Settings (DESIGN 6.2, 19.7): a tonal circle with a person, and a badge with the number of
 * waiting join requests for the owner. The description says how many; the badge's number isn't read again (review
 * A11Y-08).
 */
@Composable
private fun SettingsButton(onOpenSettings: () -> Unit) {
    val requests = LocalJoinRequestCount.current
    val description = if (requests > 0) {
        pluralStringResource(R.plurals.open_settings_with_requests, requests, requests)
    } else {
        stringResource(R.string.open_settings)
    }
    // The tour's last step points here (DESIGN 5.8); the key matches TourTarget.Settings.
    FilledTonalIconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp).coachMark("tour_settings")) {
        BadgedBox(
            badge = {
                if (requests > 0) {
                    // Material's badge has a fixed size, so its number grows only to 1.2× text, like tab labels.
                    val density = LocalDensity.current
                    val capped = Density(density.density, density.fontScale.coerceAtMost(BADGE_MAX_FONT_SCALE))
                    CompositionLocalProvider(LocalDensity provides capped) {
                        Badge { Text(requests.toString(), Modifier.clearAndSetSemantics { }) }
                    }
                }
            },
        ) {
            Icon(Icons.Rounded.Person, contentDescription = description)
        }
    }
}

/** Scaffold for a pushed screen: small top app bar with a back button, on the page's color (DESIGN 19.2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val page = SpoilNotSurfaces.current.page
    Scaffold(
        modifier = modifier,
        containerColor = page,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = page, scrolledContainerColor = page),
            )
        },
    ) { padding -> CenteredContent(padding, content) }
}

@Composable
internal fun CenteredContent(padding: PaddingValues, content: @Composable (PaddingValues) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = MaxContentWidth).fillMaxSize()) { content(padding) }
    }
}
