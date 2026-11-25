package com.parcha.spoilnot.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The surfaces of the 2026-11 look (DESIGN 19.2): a tinted [page] with rows and cards one step away from it, so a
 * group of rows reads as one block without outlines or dividers.
 *
 * The Material roles differ in light and dark. In light the page is `surfaceContainerLow` and rows are the lightest
 * surface; in dark the page is `surface` and rows are one step lighter, because dark's `surfaceContainerLowest` is
 * darker than the page and rows would sink into it.
 *
 * @property page behind a screen's content.
 * @property row list rows, plain cards, search and quick-add fields on a page.
 * @property nestedRow rows inside a highlight card (`primaryContainer`).
 * @property field text fields and pickers on a sheet (`surfaceContainerLow`).
 * @property tile the leading tile of a row without a status, such as No expiry date.
 * @property dialogRow a dialog's choices (on `surfaceContainerHigh`): in dark [field] is the dialog's own color.
 */
@Immutable
data class AppSurfaces(
    val page: Color,
    val row: Color,
    val nestedRow: Color,
    val field: Color,
    val tile: Color,
    val dialogRow: Color,
)

/** The surfaces for [scheme], which works for the fixed themes and the wallpaper's colors alike. */
fun appSurfaces(scheme: ColorScheme, dark: Boolean): AppSurfaces = if (dark) {
    AppSurfaces(
        page = scheme.surface,
        row = scheme.surfaceContainer,
        nestedRow = scheme.surfaceContainerLow,
        field = scheme.surfaceContainerHigh,
        tile = scheme.surfaceContainerHighest,
        dialogRow = scheme.surfaceContainerHighest,
    )
} else {
    AppSurfaces(
        page = scheme.surfaceContainerLow,
        row = scheme.surfaceContainerLowest,
        nestedRow = scheme.surfaceContainerLowest,
        field = scheme.surfaceContainerLowest,
        tile = scheme.surfaceContainer,
        dialogRow = scheme.surfaceContainerLowest,
    )
}

val LocalAppSurfaces = staticCompositionLocalOf { appSurfaces(LightColors, dark = false) }

/** Shortcut for the current theme's surfaces. */
object SpoilNotSurfaces {
    val current: AppSurfaces
        @Composable
        @ReadOnlyComposable
        get() = LocalAppSurfaces.current
}
