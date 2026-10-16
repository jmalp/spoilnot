package com.parcha.spoilnot.feature.household.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.parcha.spoilnot.feature.household.domain.InviteCodes

/** Shows a normalized code as "ABCDE-23456" while the field holds "ABCDE23456" (DESIGN 5.1). */
object InviteCodeTransformation : VisualTransformation {
    private const val GROUP = 5

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val hasHyphen = raw.length > GROUP
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (hasHyphen && offset > GROUP) offset + 1 else offset

            override fun transformedToOriginal(offset: Int) = if (hasHyphen &&
                offset > GROUP
            ) {
                (offset - 1).coerceAtMost(raw.length)
            } else {
                offset.coerceAtMost(raw.length)
            }
        }
        return TransformedText(AnnotatedString(InviteCodes.format(raw)), mapping)
    }
}
