package com.parcha.spoilnot.core.designsystem.motion

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** True when the system's Remove animations setting is on (DESIGN 6.4: respect reduce motion). */
fun Context.reduceMotion(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Remove animations as the app's theme last saw it; [com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme]. */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * Whether to leave animations out (DESIGN 6.4). It follows Remove animations while the app runs (after v1, review
 * PERF-10: it used to be read once per screen, so turning it on changed nothing on screens already shown).
 */
@Composable
fun rememberReduceMotion(): Boolean = LocalReduceMotion.current

/**
 * Remove animations, watched with one observer for the whole app; the theme provides it as [LocalReduceMotion], so
 * rows and charts don't each register their own.
 */
@Composable
fun observeReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember(context) { mutableStateOf(context.reduceMotion()) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = context.reduceMotion()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        // A change between the first read and the registration.
        reduce = context.reduceMotion()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduce
}
