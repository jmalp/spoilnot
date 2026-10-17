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

/** A product's detail screen, pushed on the tab that opened it. */
@Serializable
data class ProductDetailRoute(val productId: String, val tab: TopLevelDestination) : NavKey

/** Settings > Notifications, pushed on the same tab as Settings. */
@Serializable
data class NotificationSettingsRoute(val tab: TopLevelDestination) : NavKey
