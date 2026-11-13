package com.parcha.spoilnot.core.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.parcha.spoilnot.core.write.Undo

/**
 * A snackbar message, with Undo when the action can be undone. [args] fill the string's placeholders. With [count],
 * [text] is a plurals resource chosen by it (after v1, "Copied 5 meals from last week").
 */
data class UiMessage(
    val text: Int,
    val args: List<String> = emptyList(),
    val undo: Undo? = null,
    val count: Int? = null,
) {
    constructor(@StringRes text: Int, arg: String?, undo: Undo? = null) : this(text, listOfNotNull(arg), undo)

    companion object {
        /** A plurals message whose only placeholder is [count]. */
        fun plural(@PluralsRes text: Int, count: Int, undo: Undo? = null) =
            UiMessage(text, listOf(count.toString()), undo, count)
    }
}
