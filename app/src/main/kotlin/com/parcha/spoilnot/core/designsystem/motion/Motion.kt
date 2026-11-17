package com.parcha.spoilnot.core.designsystem.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * The app's motion (DESIGN 6.4): its durations, its easing and the transitions between screens, so the next screen
 * moves like the others (review PERF-10). Every user of these also follows Remove animations ([rememberReduceMotion]).
 */
object Motion {
    /** A screen pushed or popped: the slide and the new screen's fade. */
    const val SCREEN_MS = 250

    /** The new screen's fade starts this late, once the old one is mostly gone. */
    const val SCREEN_FADE_IN_DELAY_MS = 50

    /** The old screen fades out faster than the new one comes in. */
    const val SCREEN_FADE_OUT_MS = 150

    /** A tab switch: one fade, of the screen drawn on top. */
    const val TAB_FADE_MS = 150

    /** With Remove animations on, pushed screens only cross-fade, this fast. */
    const val REDUCED_FADE_MS = 120

    /** Your kitchen's charts grow when they are first shown. */
    const val CHART_GROW_MS = 700

    /** One way of the loading skeleton's pulse. */
    const val SKELETON_PULSE_MS = 800

    /** A change from another phone tints its row, fading out over this long (DESIGN 6.4). */
    const val HIGHLIGHT_MS = 1_500

    /** Material's standard easing, for everything that moves or grows. */
    val Standard: Easing = FastOutSlowInEasing

    /**
     * Material's shared axis X for a pushed or popped screen: a short slide with a fade, [SCREEN_MS]; only a cross-fade
     * of [REDUCED_FADE_MS] with Remove animations on.
     */
    fun sharedAxis(forward: Boolean, reduceMotion: Boolean): ContentTransform {
        if (reduceMotion) {
            return fadeIn(tween(REDUCED_FADE_MS, easing = Standard)) togetherWith
                fadeOut(tween(REDUCED_FADE_MS, easing = Standard))
        }
        val sign = if (forward) 1 else -1
        val enter = slideInHorizontally(tween(SCREEN_MS, easing = Standard)) { sign * it / 8 } +
            fadeIn(tween(SCREEN_MS, delayMillis = SCREEN_FADE_IN_DELAY_MS, easing = Standard))
        val exit = slideOutHorizontally(tween(SCREEN_MS, easing = Standard)) { -sign * it / 8 } +
            fadeOut(tween(SCREEN_FADE_OUT_MS, easing = Standard))
        return enter togetherWith exit
    }

    /**
     * Between tabs (after v1, review PERF-09): no slide, and only the screen drawn on top fades, over the other one.
     * That is the new tab when it comes in on top ([newOnTop]), or the old one when it goes back to Home, which the
     * navigation draws underneath. A tab composed for the first time then fills one layer, not two. Instant with Remove
     * animations on.
     */
    fun tabSwitch(newOnTop: Boolean, reduceMotion: Boolean): ContentTransform = when {
        reduceMotion -> EnterTransition.None togetherWith ExitTransition.None
        newOnTop -> fadeIn(tween(TAB_FADE_MS, easing = Standard)) togetherWith ExitTransition.None
        else -> EnterTransition.None togetherWith fadeOut(tween(TAB_FADE_MS, easing = Standard))
    }
}
