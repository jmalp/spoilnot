package com.parcha.spoilnot.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * Settings > Appearance > Colors (DESIGN 6.4): the wallpaper's Material You colors, the Spoil Not green, or one of
 * the preset themes generated from the owner's palettes (tools/themes). [id] is what the phone stores.
 */
enum class ColorTheme(val id: String) {
    Wallpaper("wallpaper"),
    SpoilNot("spoilnot"),
    Fiery("fiery"),
    SummerFun("summer_fun"),
    EnchantedForest("enchanted_forest"),
    WatermelonSorbet("watermelon_sorbet"),
    ContrastPop("contrast_pop"),
    ;

    /** This theme's fixed scheme; null for [Wallpaper], whose colors come from the phone. */
    fun scheme(dark: Boolean): ColorScheme? = when (this) {
        Wallpaper -> null
        SpoilNot -> if (dark) DarkColors else LightColors
        Fiery -> if (dark) FieryDark else FieryLight
        SummerFun -> if (dark) SummerFunDark else SummerFunLight
        EnchantedForest -> if (dark) EnchantedForestDark else EnchantedForestLight
        WatermelonSorbet -> if (dark) WatermelonSorbetDark else WatermelonSorbetLight
        ContrastPop -> if (dark) ContrastPopDark else ContrastPopLight
    }

    companion object {
        /** Material You needs Android 12; older phones show the Spoil Not green instead. */
        val wallpaperAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        fun fromId(id: String?): ColorTheme? = entries.firstOrNull { it.id == id }

        /** The themes Settings offers on this phone. */
        val offered: List<ColorTheme> get() = if (wallpaperAvailable) entries else entries - Wallpaper
    }
}

/** The scheme [theme] uses on this phone, in light or dark. */
@Composable
fun colorSchemeFor(theme: ColorTheme, dark: Boolean): ColorScheme {
    theme.scheme(dark)?.let { return it }
    if (!ColorTheme.wallpaperAvailable) return if (dark) DarkColors else LightColors
    val context = LocalContext.current
    return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

@Composable
fun SpoilNotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: ColorTheme = ColorTheme.Wallpaper,
    content: @Composable () -> Unit,
) {
    // Status colors are fixed and never follow the theme, so expiry stays recognizable (DESIGN 6.4).
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors) {
        MaterialTheme(
            colorScheme = colorSchemeFor(colorTheme, darkTheme),
            typography = SpoilNotTypography,
            shapes = SpoilNotShapes,
            content = content,
        )
    }
}

/** Shortcut for the fixed status palette of the current theme. */
object SpoilNotStatus {
    val colors: StatusColors
        @Composable get() = LocalStatusColors.current
}
