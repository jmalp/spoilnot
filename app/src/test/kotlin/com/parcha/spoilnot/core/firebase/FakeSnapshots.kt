package com.parcha.spoilnot.core.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk

/**
 * A stored document with [fields], read through the getters the mappers use (no Firestore needed). The typed getters
 * throw on a field of another type, as the SDK's do, so a mapper that still uses one fails its wrong-type test (after
 * v1, REL-04); the mappers read with `get` and safe casts ([string], [double], …).
 */
fun fakeSnapshot(id: String, fields: Map<String, Any?>, pendingWrites: Boolean = false): DocumentSnapshot =
    mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { getString(any<String>()) } answers { typed<String>(fields, firstArg()) }
        every { this@mockk.get(any<String>()) } answers { fields[firstArg()] }
        every { this@mockk.get(any<String>(), any<DocumentSnapshot.ServerTimestampBehavior>()) } answers {
            fields[firstArg()]
        }
        every { getLong(any<String>()) } answers { typed<Number>(fields, firstArg())?.toLong() }
        every { getDouble(any<String>()) } answers { typed<Number>(fields, firstArg())?.toDouble() }
        every { getBoolean(any<String>()) } answers { typed<Boolean>(fields, firstArg()) }
        every { getTimestamp(any<String>()) } answers { typed<Timestamp>(fields, firstArg()) }
        every { getTimestamp(any<String>(), any()) } answers { typed<Timestamp>(fields, firstArg()) }
        every { contains(any<String>()) } answers { fields.containsKey(firstArg<String>()) }
        every { exists() } returns true
        every { metadata.hasPendingWrites() } returns pendingWrites
    }

/** The SDK's typed read: null when missing, the value when of type [T], and a RuntimeException otherwise. */
private inline fun <reified T> typed(fields: Map<String, Any?>, field: String): T? = when (val value = fields[field]) {
    null -> null
    is T -> value
    else -> throw RuntimeException("Field '$field' is not a ${T::class.simpleName}")
}
