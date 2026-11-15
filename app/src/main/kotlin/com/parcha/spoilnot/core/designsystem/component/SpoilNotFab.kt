package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText

/**
 * The app's extended floating button (DESIGN 6.8, 19.9): an icon and [text], always expanded, named by its [text].
 * In the 2026-11 look it is `primary` with `onPrimary`, at least 64 dp tall with 22 dp corners.
 *
 * Material's `ExtendedFloatingActionButton` hides its text from screen readers and takes the name from the icon, so
 * with a decorative icon TalkBack said only "Button" (review 07, A11Y-01). Here the button carries [text] as its own
 * text, like any other button: TalkBack reads "Add item, button", Voice Access and Switch Access show the name, and
 * tests and UI Automator find it by that text. The icon stays decorative so the name isn't read twice.
 */
@Composable
fun SpoilNotFab(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        text = { Text(text, style = SpoilNotText.ButtonLarge) },
        icon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp).semantics { this.text = AnnotatedString(text) },
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    )
}
