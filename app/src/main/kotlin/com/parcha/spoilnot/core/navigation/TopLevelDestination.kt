package com.parcha.spoilnot.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.parcha.spoilnot.R

/** The four bottom-bar tabs, in display order. Home is the start tab. */
enum class TopLevelDestination(
    val root: NavKey,
    @param:StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home(HomeRoute, R.string.tab_home, Icons.Rounded.Home, Icons.Outlined.Home),
    Pantry(PantryRoute, R.string.tab_pantry, Icons.Rounded.Kitchen, Icons.Outlined.Kitchen),
    Recipes(
        RecipesRoute,
        R.string.tab_recipes,
        Icons.AutoMirrored.Rounded.MenuBook,
        Icons.AutoMirrored.Outlined.MenuBook,
    ),
    Groceries(GroceriesRoute, R.string.tab_groceries, Icons.Rounded.ShoppingCart, Icons.Outlined.ShoppingCart),
}
