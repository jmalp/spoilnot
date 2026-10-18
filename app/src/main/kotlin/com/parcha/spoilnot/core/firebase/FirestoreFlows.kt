package com.parcha.spoilnot.core.firebase

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.WriteBatch
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.error.QuotaSignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** Firestore helpers for data/ repositories only. */

fun DocumentReference.snapshotFlow(metadata: MetadataChanges = MetadataChanges.EXCLUDE): Flow<DocumentSnapshot> =
    callbackFlow {
        val registration = addSnapshotListener(metadata) { snapshot, error ->
            when {
                error != null -> close(error.toAppError())
                snapshot != null -> trySend(snapshot)
            }
        }
        awaitClose { registration.remove() }
    }

fun Query.snapshotFlow(metadata: MetadataChanges = MetadataChanges.EXCLUDE): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener(metadata) { snapshot, error ->
        when {
            error != null -> close(error.toAppError())
            snapshot != null -> trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}

/**
 * Retries a household listener that the server refused because this device's own writes have not landed yet.
 * Right after creating or joining a household the listener can reach the server before the membership write,
 * and the rules then deny it. A second refusal after our pending writes are acknowledged is real.
 */
fun <T> Flow<T>.retryAfterOwnWrites(db: FirebaseFirestore, attempts: Long = 2): Flow<T> = retryWhen { cause, attempt ->
    if (cause !is AppError.PermissionDenied || attempt >= attempts) return@retryWhen false
    withTimeoutOrNull(PENDING_WRITES_WAIT_MS) { db.waitForPendingWrites().await() }
    delay(RETRY_DELAY_MS * (attempt + 1))
    true
}

/** Awaits a task, mapping Firebase failures to [AppError]. */
suspend fun <T> Task<T>.awaitMapped(): T = try {
    await()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    throw e.toAppError()
}

/**
 * Commits a batch that may stay queued offline (DESIGN 9.5): returns once the server accepts it, or after a short
 * wait while it remains queued in the local cache. Rule rejections that arrive in time are thrown.
 */
suspend fun WriteBatch.commitQueued() {
    withTimeoutOrNull(QUEUED_WRITE_WAIT_MS) { commit().awaitMapped() }
}

/** Commits a batch that must reach the server, such as leaving a household before the cache is cleared. */
suspend fun WriteBatch.commitConfirmed() {
    try {
        withTimeout(CONFIRMED_WRITE_TIMEOUT_MS) { commit().awaitMapped() }
    } catch (e: TimeoutCancellationException) {
        throw AppError.Offline(e)
    }
}

fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this

    is FirebaseFirestoreException -> when (code) {
        Code.PERMISSION_DENIED, Code.UNAUTHENTICATED -> AppError.PermissionDenied(this)
        Code.RESOURCE_EXHAUSTED -> AppError.QuotaExceeded(this).also { QuotaSignal.report() }
        Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED -> AppError.Offline(this)
        Code.NOT_FOUND -> AppError.NotFound(this)
        Code.ALREADY_EXISTS, Code.ABORTED, Code.FAILED_PRECONDITION -> AppError.Conflict(this)
        else -> AppError.Unknown(this)
    }

    is FirebaseNetworkException -> AppError.Offline(this)

    else -> AppError.Unknown(this)
}

private const val QUEUED_WRITE_WAIT_MS = 3_000L
private const val PENDING_WRITES_WAIT_MS = 5_000L
private const val RETRY_DELAY_MS = 250L
private const val CONFIRMED_WRITE_TIMEOUT_MS = 15_000L
