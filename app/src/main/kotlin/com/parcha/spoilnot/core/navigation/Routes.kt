package com.parcha.spoilnot.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data object PantryRoute : NavKey

@Serializable
data object RecipesRoute : NavKey

@Serializable
data object GroceriesRoute : NavKey

/**
 * Settings opens from the avatar button on any tab, on top of that tab's back stack (DESIGN 6.2). The tab is part
 * of the key so two tabs can each have Settings open without sharing saved state.
 */
@Serializable
data class SettingsRoute(val tab: TopLevelDestination) : NavKey

/** Settings > Household, pushed on the same tab as Settings. */
@Serializable
data class HouseholdSettingsRoute(val tab: TopLevelDestination) : NavKey

/** Settings > Notifications, pushed on the same tab as Settings. */
@Serializable
data class NotificationSettingsRoute(val tab: TopLevelDestination) : NavKey

/** A saved recipe (DESIGN 5.5), pushed on the tab that opened it. */
@Serializable
data class RecipeDetailRoute(val recipeId: String, val tab: TopLevelDestination) : NavKey

/**
 * Create manually ([recipeId] null) or edit. A failed import prefills the title, image and link (DESIGN 11.2).
 */
@Serializable
data class RecipeEditRoute(
    val recipeId: String?,
    val tab: TopLevelDestination,
    val prefillTitle: String? = null,
    val prefillImageUrl: String? = null,
    val prefillSourceUrl: String? = null,
) : NavKey

/** The preview of a recipe fetched from a link (Paste link and the share sheet). */
@Serializable
data class RecipeImportRoute(val url: String?, val tab: TopLevelDestination) : NavKey

/**
 * The built-in browser (DESIGN 5.5). [fallback] is true when a fetched page had no recipe, so a page without one
 * offers Create manually with [prefillTitle] and [prefillImageUrl].
 */
@Serializable
data class RecipeBrowserRoute(
    val url: String?,
    val tab: TopLevelDestination,
    val fallback: Boolean = false,
    val prefillTitle: String? = null,
    val prefillImageUrl: String? = null,
) : NavKey

/** Cook mode at [servings] (the detail screen's stepper). */
@Serializable
data class CookModeRoute(val recipeId: String, val servings: Int?, val tab: TopLevelDestination) : NavKey

/** Settings > About, pushed on the same tab as Settings. */
@Serializable
data class AboutRoute(val tab: TopLevelDestination) : NavKey
