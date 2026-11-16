package com.parcha.spoilnot.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A change from another phone waits until the list stops moving (DESIGN 6.4, review PERF-08). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HoldWhileScrollingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aChangeWaitsWhileTheListIsDraggedAndShowsOnceItStops() {
        var value by mutableIntStateOf(1)
        compose.setContent {
            val state = rememberLazyListState()
            val shown = rememberHeldWhileScrolling(value, state)
            Column {
                Text("shown $shown", Modifier.testTag("shown"))
                LazyColumn(state = state, modifier = Modifier.height(300.dp).testTag("list")) {
                    items(100) { Text("Row $it", Modifier.height(48.dp)) }
                }
            }
        }

        // Not scrolling: a change shows at once.
        value = 2
        compose.onNodeWithTag("shown").assertTextEquals("shown 2")

        // The finger is down and the list is moving: the change waits.
        compose.onNodeWithTag("list").performTouchInput {
            down(center)
            moveBy(Offset(0f, -200f))
            moveBy(Offset(0f, -200f))
        }
        value = 3
        compose.onNodeWithTag("shown").assertTextEquals("shown 2")

        // Released and settled: it shows.
        compose.onNodeWithTag("list").performTouchInput { up() }
        compose.onNodeWithTag("shown").assertTextEquals("shown 3")
    }
}
