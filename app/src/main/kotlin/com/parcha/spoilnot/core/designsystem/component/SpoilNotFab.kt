package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString

/**
 * The app's extended floating button (DESIGN 6.8): an icon and [text], always expanded, named by its [text].
 *
 * Material's `ExtendedFloatingActionButton` hides its text from screen readers and takes the name from the icon, so
 * with a decorative icon TalkBack said only "Button" (review 07, A11Y-01). Here the button carries [text] as its own
 * text, like any other button: TalkBack reads "Add item, button", Voice Access and Switch Access show the name, and
 * tests and UI Automator find it by that text. The icon stays decorative so the name isn't read twice.
 */
@Composable
fun SpoilNotFab(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        text = { Text(text) },
        icon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        modifier = modifier.semantics { this.text = AnnotatedString(text) },
    )
}
