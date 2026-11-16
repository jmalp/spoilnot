package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The text field, search field and filter chip of the 2026-11 look (DESIGN 19.10), as screen readers and taps meet them. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FieldsAndFiltersTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aFieldIsReadWithItsLabelAboveAndItsMessage() {
        compose.setContent {
            SpoilNotTheme(colorTheme = ColorTheme.SpoilNot) {
                var name by remember { mutableStateOf("") }
                SpoilNotTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Name",
                    helper = "Shown to people in your household",
                    modifier = Modifier.testTag("field"),
                )
            }
        }
        // One node: the label and the helper are read with what is typed, as Material's fields are.
        compose.onNode(hasSetTextAction() and hasText("Name")).assertTextContains("Shown to people in your household")
        compose.onNodeWithTag("field").performTextInput("Rice")
        compose.onNodeWithTag("field").assertTextContains("Rice")
    }

    @Test
    fun tappingTheLabelFocusesTheField() {
        compose.setContent {
            SpoilNotTheme(colorTheme = ColorTheme.SpoilNot) {
                SpoilNotTextField(value = "", onValueChange = {}, label = "Name", modifier = Modifier.testTag("field"))
            }
        }
        compose.onNodeWithText("Name", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("field").assertIsFocused()
    }

    @Test
    fun aWrongFieldSaysSoToScreenReadersAndShowsTheMessage() {
        compose.setContent {
            SpoilNotTheme(colorTheme = ColorTheme.SpoilNot) {
                SpoilNotTextField(
                    value = "",
                    onValueChange = {},
                    label = "Name",
                    helper = "Not shown while wrong",
                    error = "Enter a name.",
                    modifier = Modifier.testTag("field"),
                )
            }
        }
        compose.onNodeWithTag("field")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Enter a name."))
            .assertTextContains("Enter a name.")
        compose.onNodeWithText("Not shown while wrong", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theSearchFieldIsNamedByItsPlaceholderAndClears() {
        var value = ""
        compose.setContent {
            SpoilNotTheme(colorTheme = ColorTheme.SpoilNot) {
                var text by remember { mutableStateOf("") }
                SearchField(
                    value = text,
                    onValueChange = {
                        text = it
                        value = it
                    },
                    placeholder = "Search pantry",
                    clearLabel = "Clear",
                    modifier = Modifier.testTag("search"),
                )
            }
        }
        compose.onNode(hasSetTextAction() and hasText("Search pantry")).performTextInput("milk")
        assertEquals("milk", value)
        compose.onNode(hasContentDescription("Clear")).performClick()
        assertEquals("", value)
        compose.onNode(hasContentDescription("Clear")).assertDoesNotExist()
    }

    @Test
    fun aFilterChipIsATogglableChoice() {
        compose.setContent {
            SpoilNotTheme(colorTheme = ColorTheme.SpoilNot) {
                var chosen by remember { mutableStateOf(false) }
                Column {
                    SpoilNotFilterChip("Fridge", chosen, onClick = { chosen = !chosen }, Modifier.testTag("chip"))
                }
            }
        }
        // Material's chip underneath: a checkbox that is selected or not.
        compose.onNodeWithTag("chip")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertIsNotSelected()
        compose.onNodeWithTag("chip").performClick()
        compose.onNodeWithTag("chip").assertIsSelected()
    }
}
