package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText

/**
 * A round icon button that sits over a header or a picture (DESIGN 19.7, 19.9): 48 dp, on the `row` color, so it stands
 * out from whatever is under it. [contentDescription] names it.
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = SpoilNotSurfaces.current.row,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) { Icon(icon, contentDescription = contentDescription) }
}

/** The round Back of a pushed screen (DESIGN 19.7). */
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack, modifier)
}

/**
 * Scaffold for a pushed screen in the 2026-11 look (DESIGN 19.7): the round Back on its own line with the screen's
 * [actions] at its end, then the [title] in [SpoilNotText.ScreenTitle], all on the page's color and pinned. Screens
 * keep [DetailScaffold] until their step.
 */
@Composable
fun PushedScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val page = SpoilNotSurfaces.current.page
    Scaffold(
        modifier = modifier,
        containerColor = page,
        snackbarHost = snackbarHost,
        bottomBar = bottomBar,
        topBar = {
            Column(
                Modifier
                    .background(page)
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                    )
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BackButton(onBack)
                    Spacer(Modifier.weight(1f))
                    actions()
                }
                Text(
                    title,
                    style = SpoilNotText.ScreenTitle,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp).semantics { heading() },
                )
            }
        },
    ) { padding -> CenteredContent(padding, content) }
}

/**
 * The bar under a screen's content that holds its main button (DESIGN 19.9, 19.11), such as Start cooking or Save on
 * a recipe preview: `surfaceContainer`, so the content scrolls clear of it.
 */
@Composable
fun BottomActionBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                )
                .heightIn(min = 80.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) { content() }
    }
}
