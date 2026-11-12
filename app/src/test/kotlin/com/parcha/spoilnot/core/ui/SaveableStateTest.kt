package com.parcha.spoilnot.core.ui

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The plan and recipe sheets' picks survive a recreation (after v1, REL-09). */
@RunWith(RobolectricTestRunner::class)
class SaveableStateTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun picksComeBackAfterTheActivityIsRecreated() {
        val restoration = StateRestorationTester(compose)
        var list: MutableList<String>? = null
        var map: MutableMap<Int, String>? = null
        restoration.setContent {
            list = rememberSaveableList("alice")
            map = rememberSaveableMap()
        }
        compose.runOnIdle {
            list!!.add("bob")
            map!![2] = "Spring onions"
        }
        list = null
        map = null
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals(listOf("alice", "bob"), list)
            assertEquals(mapOf(2 to "Spring onions"), map!!.toMap())
        }
    }
}
