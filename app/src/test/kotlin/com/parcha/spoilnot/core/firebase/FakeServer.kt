package com.parcha.spoilnot.core.firebase

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import io.mockk.every
import io.mockk.mockk

/**
 * The documents a change mapper's writes leave on the server, for round trips without Firestore: a set replaces the
 * document, a merge or an update changes only the fields it names, `FieldValue.delete()` removes one, and a server
 * timestamp is stored as [SERVER_TIME]. Increments are stored as the FieldValue itself.
 */
class FakeServer {
    val docs = mutableMapOf<String, MutableMap<String, Any?>>()

    /** Each write's path, kind ("set", "merge", "update" or "delete") and fields, in order. */
    val writes = mutableListOf<Triple<String, String, Map<String, Any?>>>()

    val db: FirebaseFirestore = mockk {
        every { document(any()) } answers { ref(firstArg()) }
    }

    val batch: WriteBatch = mockk {
        every { set(any<DocumentReference>(), any<Any>()) } answers {
            write(firstArg<DocumentReference>().path, "set", fields(secondArg()))
            self as WriteBatch
        }
        every { set(any<DocumentReference>(), any<Any>(), any<SetOptions>()) } answers {
            check(thirdArg<SetOptions>() == SetOptions.merge()) { "only merge is used" }
            write(firstArg<DocumentReference>().path, "merge", fields(secondArg()))
            self as WriteBatch
        }
        every { delete(any<DocumentReference>()) } answers {
            val path = firstArg<DocumentReference>().path
            writes += Triple(path, "delete", emptyMap())
            docs.remove(path)
            self as WriteBatch
        }
        every { update(any<DocumentReference>(), any<Map<String, Any>>()) } answers {
            val path = firstArg<DocumentReference>().path
            check(path in docs) { "update of a missing document: $path" }
            write(path, "update", fields(secondArg()))
            self as WriteBatch
        }
    }

    /** The stored document at [path] read through the getters the mappers use. */
    fun snapshot(path: String): DocumentSnapshot = fakeSnapshot(path.substringAfterLast('/'), docs.getValue(path))

    private fun ref(path: String): DocumentReference = mockk {
        every { this@mockk.path } returns path
        every { id } returns path.substringAfterLast('/')
    }

    @Suppress("UNCHECKED_CAST")
    private fun fields(value: Any): Map<String, Any?> = value as Map<String, Any?>

    private fun write(path: String, kind: String, fields: Map<String, Any?>) {
        writes += Triple(path, kind, fields)
        val doc = if (kind == "set") {
            mutableMapOf<String, Any?>().also { docs[path] = it }
        } else {
            docs.getOrPut(path) {
                mutableMapOf()
            }
        }
        for ((key, value) in fields) {
            when {
                value == FieldValue.delete() -> doc.remove(key)
                value == FieldValue.serverTimestamp() -> doc[key] = SERVER_TIME
                else -> doc[key] = value
            }
        }
    }

    companion object {
        const val SERVER_TIME = "(server time)"
    }
}
