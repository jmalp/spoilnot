package com.parcha.spoilnot.core.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Navigation across the four tabs, each with its own back stack (DESIGN 6.2). Plain Kotlin over mutable lists
 * so it can be unit tested; in the app the lists are Navigation 3 [androidx.navigation3.runtime.NavBackStack]s.
 *
 * Back pops the current tab; at a tab's root it returns to Home; at Home's root it leaves the app.
 * Screens matching [closesOnLeave] (Settings and its pages) are closed when their tab is left, so another tab never
 * comes back with Settings open (beta feedback); everything else keeps its place.
 */
class TabNavigator(
    private val backStacks: Map<TopLevelDestination, MutableList<NavKey>>,
    private val currentTabHolder: () -> TopLevelDestination,
    private val setCurrentTab: (TopLevelDestination) -> Unit,
    private val closesOnLeave: (NavKey) -> Boolean = { false },
) {
    init {
        require(backStacks.keys == TopLevelDestination.entries.toSet()) { "Every tab needs a back stack" }
        require(backStacks.all { it.value.isNotEmpty() }) { "Back stacks start with the tab root" }
    }

    val currentTab: TopLevelDestination get() = currentTabHolder()

    /** Keys to display: Home's stack underneath, then the current tab's stack, so back exits through Home. */
    val visibleKeys: List<NavKey>
        get() = if (currentTab == START) {
            stackOf(START)
        } else {
            stackOf(START) + stackOf(currentTab)
        }

    /** Selecting the current tab again pops it to its root; selecting another tab restores its stack. */
    fun selectTab(tab: TopLevelDestination) {
        if (tab == currentTab) {
            val stack = stackOf(tab)
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        } else {
            leave(currentTab)
            setCurrentTab(tab)
        }
    }

    /** Shows [tab] at its root, for example when a notification opens the filtered Pantry list. */
    fun showRoot(tab: TopLevelDestination) {
        val stack = stackOf(tab)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        if (tab != currentTab) {
            leave(currentTab)
            setCurrentTab(tab)
        }
    }

    fun navigate(key: NavKey) {
        stackOf(currentTab).add(key)
    }

    /** Replaces the current screen, for example an import preview with the saved recipe. */
    fun replace(key: NavKey) {
        val stack = stackOf(currentTab)
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        stack.add(key)
    }

    /** Returns false when there is nothing left to pop, so the system should handle back. */
    fun goBack(): Boolean {
        val stack = stackOf(currentTab)
        return when {
            stack.size > 1 -> {
                stack.removeAt(stack.lastIndex)
                true
            }

            currentTab != START -> {
                leave(currentTab)
                setCurrentTab(START)
                true
            }

            else -> false
        }
    }

    /** Closes the screens on top of [tab]'s stack that do not stay open when the tab is left. */
    private fun leave(tab: TopLevelDestination) {
        val stack = stackOf(tab)
        while (stack.size > 1 && closesOnLeave(stack.last())) stack.removeAt(stack.lastIndex)
    }

    fun stackOf(tab: TopLevelDestination): MutableList<NavKey> = backStacks.getValue(tab)

    companion object {
        val START = TopLevelDestination.Home
    }
}
