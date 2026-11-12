package com.parcha.spoilnot.core.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import java.time.Instant
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/*
 * Reading another client's documents (after v1, DESIGN 8.6, review REL-04). The typed getters of the SDK
 * (`getString`, `getDouble`, `getLong`, `getBoolean`, `getTimestamp`) throw when a field holds another type, which
 * ended a whole list's listener: one document with `quantity: "2"` blanked Pantry, Groceries and Home on every phone
 * of the household. These read a field whatever its type: another type reads as missing, so the mapper's default
 * applies, and [mapEach] skips a document that still fails to map.
 */

/** The field as text, or null when it is missing or holds another type. */
fun DocumentSnapshot.string(field: String): String? = get(field) as? String

/** The field as a number (an integer or a double), or null when it is missing or holds another type. */
fun DocumentSnapshot.double(field: String): Double? = (get(field) as? Number)?.toDouble()

/** The field as a whole number (a double is cut, as `getLong` does), or null when missing or of another type. */
fun DocumentSnapshot.long(field: String): Long? = (get(field) as? Number)?.toLong()

/** [long] as an Int. */
fun DocumentSnapshot.int(field: String): Int? = long(field)?.toInt()

/** The field as true or false, or null when it is missing or holds another type. */
fun DocumentSnapshot.boolean(field: String): Boolean? = get(field) as? Boolean

/**
 * The field as a time, or null when it is missing or holds another type. With [estimate] a server timestamp that is
 * still pending reads as the phone's estimate; without it, as null (the SDK's default).
 */
fun DocumentSnapshot.instant(field: String, estimate: Boolean = true): Instant? {
    val value = if (estimate) get(field, ServerTimestampBehavior.ESTIMATE) else get(field)
    return (value as? Timestamp)?.toInstant()
}

/** The field as a list, or null when it is missing or holds another type. */
fun DocumentSnapshot.list(field: String): List<*>? = get(field) as? List<*>

/** The text entries of a list field; entries of another type are left out, and anything but a list reads as empty. */
fun DocumentSnapshot.strings(field: String): List<String> = list(field)?.filterIsInstance<String>().orEmpty()

/** The map entries of a list field; entries of another type are left out, and anything but a list reads as empty. */
fun DocumentSnapshot.maps(field: String): List<Map<*, *>> = list(field)?.filterIsInstance<Map<*, *>>().orEmpty()

/** The field as a map, or null when it is missing or holds another type. */
fun DocumentSnapshot.fieldMap(field: String): Map<*, *>? = get(field) as? Map<*, *>

/**
 * Maps each document on its own (after v1, REL-04): one that fails to map is skipped and logged, and the others still
 * show. A mapper may also return null for a document it leaves out on purpose (an unknown meal slot).
 */
inline fun <T : Any> List<DocumentSnapshot>.mapEach(map: (DocumentSnapshot) -> T?): List<T> = mapNotNull { document ->
    try {
        map(document)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        skipped(document, e)
        null
    }
}

/** A document [mapEach] skipped: logged in debug builds (Timber), never shown. */
fun skipped(document: DocumentSnapshot, error: Exception) {
    Timber.w(error, "Skipped a document that doesn't map: %s", document.id)
}
