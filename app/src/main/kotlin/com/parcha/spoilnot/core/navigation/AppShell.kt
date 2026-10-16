package com.parcha.spoilnot.core.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.parcha.spoilnot.feature.grocery.ui.GroceriesScreen
import com.parcha.spoilnot.feature.home.ui.HomeScreen
import com.parcha.spoilnot.feature.pantry.ui.PantryScreen
import com.parcha.spoilnot.feature.recipes.ui.RecipesScreen
import com.parcha.spoilnot.feature.settings.ui.SettingsScreen

/** The signed-in app: four tabs in a bottom NavigationBar, each with its own back stack (DESIGN 6.2). */
@Composable
fun AppShell(modifier: Modifier = Modifier) {
    var currentTab by rememberSaveable { mutableStateOf(TabNavigator.START) }
    val backStacks = TopLevelDestination.entries.associateWith { tab -> key(tab) { rememberNavBackStack(tab.root) } }
    val navigator = remember(backStacks) { TabNavigator(backStacks, { currentTab }, { currentTab = it }) }

    val provider = entryProvider<NavKey> {
        entry<HomeRoute> { HomeScreen(onOpenSettings = { navigator.navigate(SettingsRoute(navigator.currentTab)) }) }
        entry<PantryRoute> {
            PantryScreen(onOpenSettings = { navigator.navigate(SettingsRoute(navigator.currentTab)) })
        }
        entry<RecipesRoute> {
            RecipesScreen(onOpenSettings = { navigator.navigate(SettingsRoute(navigator.currentTab)) })
        }
        entry<GroceriesRoute> {
            GroceriesScreen(onOpenSettings = { navigator.navigate(SettingsRoute(navigator.currentTab)) })
        }
        entry<SettingsRoute> { SettingsScreen(onBack = { navigator.goBack() }) }
    }

    // Decorate every tab's stack on every composition so saved state and ViewModels of hidden tabs survive.
    val entriesByTab: Map<TopLevelDestination, List<NavEntry<NavKey>>> =
        TopLevelDestination.entries.associateWith { tab ->
            key(tab) {
                rememberDecoratedNavEntries(
                    backStack = backStacks.getValue(tab),
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = provider,
                )
            }
        }
    val visibleEntries = if (currentTab == TabNavigator.START) {
        entriesByTab.getValue(TabNavigator.START)
    } else {
        entriesByTab.getValue(TabNavigator.START) + entriesByTab.getValue(currentTab)
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { tab ->
                    val selected = tab == currentTab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigator.selectTab(tab) },
                        icon = {
                            Icon(if (selected) tab.selectedIcon else tab.unselectedIcon, contentDescription = null)
                        },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavDisplay(
            entries = visibleEntries,
            onBack = { navigator.goBack() },
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
    }
}
