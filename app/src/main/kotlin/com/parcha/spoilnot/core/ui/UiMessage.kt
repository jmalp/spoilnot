package com.parcha.spoilnot.core.ui

import androidx.annotation.StringRes
import com.parcha.spoilnot.core.write.Undo

/** A snackbar message, with Undo when the action can be undone. [args] fill the string's placeholders. */
data class UiMessage(@param:StringRes val text: Int, val args: List<String> = emptyList(), val undo: Undo? = null) {
    constructor(@StringRes text: Int, arg: String?, undo: Undo? = null) : this(text, listOfNotNull(arg), undo)
}
