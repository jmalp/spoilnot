package com.parcha.spoilnot.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

@Composable
fun SpoilNotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors

        else -> LightColors
    }
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
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
