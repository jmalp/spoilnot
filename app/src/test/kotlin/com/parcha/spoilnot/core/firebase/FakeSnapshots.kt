package com.parcha.spoilnot.core.firebase

import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk

/** A stored document with [fields], read through the getters the mappers use (no Firestore needed). */
fun fakeSnapshot(id: String, fields: Map<String, Any?>): DocumentSnapshot = mockk(relaxed = true) {
    every { this@mockk.id } returns id
    every { getString(any<String>()) } answers { fields[firstArg()] as? String }
    every { this@mockk.get(any<String>()) } answers { fields[firstArg()] }
    every { getLong(any<String>()) } answers { (fields[firstArg()] as? Number)?.toLong() }
    every { getDouble(any<String>()) } answers { (fields[firstArg()] as? Number)?.toDouble() }
    every { getBoolean(any<String>()) } answers { fields[firstArg()] as? Boolean }
    every { getTimestamp(any<String>()) } returns null
    every { getTimestamp(any<String>(), any()) } returns null
    every { contains(any<String>()) } answers { fields.containsKey(firstArg<String>()) }
    every { exists() } returns true
}
