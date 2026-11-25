package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotStatus
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.core.designsystem.theme.StatusTone
import com.parcha.spoilnot.testing.Screenshots
import com.parcha.spoilnot.testing.setThemed
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shared pieces of the 2026-11 look (DESIGN 19) on one page: the header, a group of rows with status tiles, a
 * highlight card with a nested row, an accent card, a connected button group, label chips and the buttons; light and
 * dark, text at 1.0 and 2.0. The window is tall so the whole page is in the picture at 2.0 too.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class LookComponentsScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() = Screenshots.params()
    }

    @get:Rule
    val compose = createComposeRule()

    private data class Sample(val name: String, val status: String, val tone: StatusTone, val icon: ImageVector)

    private val samples = listOf(
        Sample("Cheese", "Expired", StatusTone.Expired, Icons.Rounded.DeleteOutline),
        Sample("Milk", "Tomorrow", StatusTone.Urgent, Icons.Rounded.WarningAmber),
        Sample("Greek yogurt", "2 days", StatusTone.Soon, Icons.Rounded.Schedule),
        Sample("Eggs", "6 days", StatusTone.Fresh, Icons.Rounded.CheckCircle),
        Sample("Rice", "No expiry date", StatusTone.NoDate, Icons.Rounded.Remove),
    )

    @Test
    fun thePiecesLookAndBehaveAsSpecified() {
        var picked by mutableStateOf<String?>("+1 week")
        val opened = mutableListOf<String>()
        compose.setThemed(dark, fontScale) { Page(picked, onPick = { picked = it }, onOpen = { opened += it }) }

        // Titles and group headings are headings for screen readers.
        compose.onNode(isHeading() and hasText("Pantry")).assertExists()
        compose.onNode(isHeading() and hasText("Use first")).assertExists()

        // A row with onClick is one control.
        compose.onNodeWithText("Greek yogurt").performClick()
        assertEquals(listOf("Greek yogurt"), opened)

        // The connected group is single choice, as buttons and as the radio list at large text.
        compose.onNodeWithText("+1 week").assertIsSelected()
        compose.onNodeWithText("+2 weeks").performClick()
        assertEquals("+2 weeks", picked)
        compose.onNodeWithText("+2 weeks").assertIsSelected()

        compose.onRoot().captureRoboImage(Screenshots.screenshotName("look_components", dark, fontScale))
    }

    @Composable
    private fun Page(picked: String?, onPick: (String) -> Unit, onOpen: (String) -> Unit) {
        Column(
            Modifier.fillMaxSize().background(SpoilNotSurfaces.current.page).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(title = "Pantry", eyebrow = "5 items") {
                IconButton(onClick = {}) { Icon(Icons.Rounded.AccountCircle, contentDescription = "Settings") }
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GroupHeading("Use first")
                    Column(verticalArrangement = Arrangement.spacedBy(ListGroupGap)) {
                        samples.forEachIndexed { index, sample ->
                            ListSegment(
                                position = segmentPosition(index, samples.size),
                                onClick = { onOpen(sample.name) },
                                contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            ) {
                                StatusTile(sample.tone, sample.icon)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(sample.name, style = SpoilNotText.RowTitle)
                                    Text(
                                        sample.status,
                                        style = SpoilNotText.RowStatus,
                                        color = SpoilNotStatus.colors.forTone(sample.tone),
                                    )
                                }
                            }
                        }
                    }
                }
                HighlightCard {
                    Text(
                        "Needs attention",
                        style = SpoilNotText.CardTitle,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    ListSegment(
                        position = SegmentPosition.Only,
                        color = SpoilNotSurfaces.current.nestedRow,
                        outerCorner = SpoilNotCorners.NestedOuter,
                        minHeight = ListSegmentMinHeightOneLine,
                        contentPadding = PaddingValues(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        StatusTile(StatusTone.Urgent, Icons.Rounded.WarningAmber, size = StatusTileSizeNested)
                        NameWithTrailing(
                            modifier = Modifier.weight(1f),
                            name = { Text("Milk", style = SpoilNotText.RowTitle, modifier = it) },
                            trailing = {
                                Text(
                                    "Tomorrow",
                                    style = SpoilNotText.RowStatus,
                                    color = SpoilNotStatus.colors.forTone(StatusTone.Urgent),
                                )
                            },
                        )
                    }
                    Button(
                        onClick = {},
                        modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
                        shape = CircleShape,
                    ) {
                        Text("See all 4 in Pantry", style = SpoilNotText.ButtonMedium)
                    }
                }
                AccentCard {
                    LeafBadge { Icon(Icons.Rounded.Restaurant, contentDescription = null) }
                    Column(Modifier.weight(1f)) {
                        Text("Use it up", style = SpoilNotText.RowSupporting)
                        Text("Creamy milk pudding", style = SpoilNotText.RowTitle)
                    }
                }
                ConnectedButtonGroup(
                    options = listOf("+3 days", "+1 week", "+2 weeks", "+1 month"),
                    selected = picked,
                    optionLabel = { it },
                    onPick = onPick,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelChip("Added automatically", icon = Icons.AutoMirrored.Rounded.TrendingDown)
                    LabelChip("Jordan", icon = Icons.Rounded.Person, kind = LabelChipKind.Person)
                }
                TonalButton("I cooked this", onClick = {}, icon = Icons.Rounded.Restaurant)
                Text(
                    "Text on the page",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
