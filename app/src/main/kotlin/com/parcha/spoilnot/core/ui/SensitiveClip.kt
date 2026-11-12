package com.parcha.spoilnot.core.ui

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle

/**
 * Plain text for the clipboard, marked sensitive (DESIGN 12.2, control 11): from Android 13 the copy preview hides
 * it and keyboards leave it out of their clipboard history. [label] names what was copied; it is never the text.
 */
fun sensitiveClip(label: CharSequence, text: CharSequence): ClipData = ClipData.newPlainText(label, text).apply {
    description.extras = PersistableBundle().apply { putBoolean(SENSITIVE_EXTRA, true) }
}

/** `ClipDescription.EXTRA_IS_SENSITIVE` from API 33; the same key, which older versions ignore, before it. */
internal val SENSITIVE_EXTRA: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ClipDescription.EXTRA_IS_SENSITIVE
    } else {
        "android.content.extra.IS_SENSITIVE"
    }
