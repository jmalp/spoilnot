package com.parcha.spoilnot.core.navigation

import androidx.navigation3.runtime.NavKey
import com.parcha.spoilnot.core.navigation.TopLevelDestination.Groceries
import com.parcha.spoilnot.core.navigation.TopLevelDestination.Home
import com.parcha.spoilnot.core.navigation.TopLevelDestination.Pantry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TabNavigatorTest {
    private var current = Home
    private lateinit var stacks: Map<TopLevelDestination, MutableList<NavKey>>
    private lateinit var navigator: TabNavigator

    @Before
    fun setUp() {
        current = Home
        stacks = TopLevelDestination.entries.associateWith { mutableListOf(it.root) }
        navigator = TabNavigator(stacks, { current }, { current = it })
    }

    @Test
    fun `starts on Home showing only its root`() {
        assertEquals(Home, navigator.currentTab)
        assertEquals(listOf(HomeRoute), navigator.visibleKeys)
    }

    @Test
    fun `another tab is shown on top of Home`() {
        navigator.selectTab(Pantry)
        assertEquals(listOf(HomeRoute, PantryRoute), navigator.visibleKeys)
    }

    @Test
    fun `each tab keeps its own back stack across tab switches`() {
        navigator.selectTab(Pantry)
        navigator.navigate(SettingsRoute(Pantry))
        navigator.selectTab(Groceries)
        navigator.selectTab(Pantry)
        assertEquals(listOf(HomeRoute, PantryRoute, SettingsRoute(Pantry)), navigator.visibleKeys)
        assertEquals(listOf<NavKey>(GroceriesRoute), stacks.getValue(Groceries))
    }

    @Test
    fun `reselecting the current tab pops it to its root`() {
        navigator.selectTab(Pantry)
        navigator.navigate(SettingsRoute(Pantry))
        navigator.selectTab(Pantry)
        assertEquals(listOf<NavKey>(PantryRoute), stacks.getValue(Pantry))
    }

    @Test
    fun `back pops within a tab, then returns to Home, then leaves the app`() {
        navigator.selectTab(Pantry)
        navigator.navigate(SettingsRoute(Pantry))

        assertTrue(navigator.goBack())
        assertEquals(listOf(HomeRoute, PantryRoute), navigator.visibleKeys)

        assertTrue(navigator.goBack())
        assertEquals(Home, navigator.currentTab)
        assertEquals(listOf<NavKey>(PantryRoute), stacks.getValue(Pantry))

        assertFalse(navigator.goBack())
    }

    @Test
    fun `showRoot switches to a tab and pops it to its root`() {
        navigator.selectTab(Pantry)
        navigator.navigate(SettingsRoute(Pantry))
        navigator.selectTab(Groceries)
        navigator.showRoot(Pantry)
        assertEquals(Pantry, navigator.currentTab)
        assertEquals(listOf(HomeRoute, PantryRoute), navigator.visibleKeys)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a missing tab`() {
        TabNavigator(mapOf(Home to mutableListOf<NavKey>(HomeRoute)), { Home }, {})
    }

    @Test
    fun `leaving a tab closes Settings on it but keeps other screens`() {
        val settings = { key: NavKey -> key is SettingsRoute || key is HouseholdSettingsRoute }
        val nav = TabNavigator(stacks, { current }, { current = it }, closesOnLeave = settings)
        nav.selectTab(Pantry)
        nav.navigate(ProductDetailRoute("p1", Pantry))
        nav.navigate(SettingsRoute(Pantry))
        nav.navigate(HouseholdSettingsRoute(Pantry))
        nav.selectTab(Groceries)
        nav.navigate(SettingsRoute(Groceries))
        nav.selectTab(Pantry)
        assertEquals(listOf(PantryRoute, ProductDetailRoute("p1", Pantry)), stacks.getValue(Pantry))
        assertEquals(listOf(GroceriesRoute), stacks.getValue(Groceries))
        // Leaving through Back to Home closes it as well.
        nav.navigate(SettingsRoute(Pantry))
        nav.goBack()
        nav.goBack()
        nav.goBack()
        assertEquals(Home, current)
        nav.navigate(SettingsRoute(Home))
        nav.showRoot(Pantry)
        assertEquals(listOf(HomeRoute), stacks.getValue(Home))
    }
}
