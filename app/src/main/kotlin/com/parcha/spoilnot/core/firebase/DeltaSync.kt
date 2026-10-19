package com.parcha.spoilnot.core.firebase

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenSource
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotListenOptions
import com.google.firebase.firestore.Source
import com.parcha.spoilnot.core.write.Actor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await

/**
 * Cheaper household listeners (after v1, DESIGN 9.8). Firestore bills a listener that was away more than 30 minutes
 * for every document again, which was most of the free quota. Instead each collection gets two listeners:
 *
 * - a **cache** listener on the whole collection, which is free and shows what the phone has, including its own
 *   unsent writes and whatever the other listener brings in;
 * - a **server** listener on only the documents changed since the newest one in the cache (`updatedAt` after the
 *   cursor), which costs one read per changed document (at least one per query).
 *
 * Deleting therefore leaves a tombstone ([Tombstones]) that the server listener can see; the daily job removes
 * tombstones after [Tombstones.KEEP_DAYS]. Once a day, and whenever the cache is empty, the server listener reads
 * the whole collection instead, which also drops anything the phone kept by mistake.
 */
object DeltaSync {
    /** A full read at least this often per collection and phone. */
    val FULL_SYNC_EVERY: Duration = Duration.ofHours(20)

    /** Reread a little before the newest cached change, in case two writes landed close together. */
    val CURSOR_MARGIN: Duration = Duration.ofMinutes(5)

    /**
     * Where the server listener starts: null means read everything (no cached change yet, no full read on record,
     * or the last one is older than [FULL_SYNC_EVERY]).
     */
    fun cursor(newestCached: Instant?, lastFullSync: Instant?, now: Instant): Instant? = when {
        newestCached == null || lastFullSync == null -> null

        Duration.between(lastFullSync, now) >= FULL_SYNC_EVERY -> null

        lastFullSync.isAfter(now) -> null

        // the clock moved back
        else -> newestCached.minus(CURSOR_MARGIN)
    }
}

/** Deleted household documents (after v1): the document stays, marked with `deletedAt`, until the daily job. */
object Tombstones {
    const val FIELD = "deletedAt"

    /** The reminder job deletes tombstones older than this; phones read everything at least daily. */
    const val KEEP_DAYS = 2L

    /** The fields that mark a document deleted, merged into it (rules require the stamp). */
    fun fields(actor: Actor): Map<String, Any> = mapOf(FIELD to FieldValue.serverTimestamp()) + Stamps.updated(actor)

    fun DocumentSnapshot.isTombstone(): Boolean = contains(FIELD) && get(FIELD) != null
}

/** When each collection was last read in full on this phone (local preference, not household data). */
class SyncLog(private val prefs: SharedPreferences, private val clock: Clock) {
    fun lastFullSync(path: String): Instant? = prefs.getLong(path, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    fun markFullSync(path: String) = prefs.edit().putLong(path, clock.millis()).apply()

    fun now(): Instant = clock.instant()
}

@Module
@InstallIn(SingletonComponent::class)
object SyncLogModule {
    @Provides
    @Singleton
    fun syncLog(@ApplicationContext context: Context, clock: Clock): SyncLog =
        SyncLog(context.getSharedPreferences("delta_sync", Context.MODE_PRIVATE), clock)
}

/** The live documents of a collection (tombstones left out) and whether the server has confirmed them yet. */
data class LiveDocuments(val documents: List<DocumentSnapshot>, val fromCache: Boolean)

/** See [DeltaSync]. */
fun CollectionReference.liveDocuments(db: FirebaseFirestore, log: SyncLog): Flow<LiveDocuments> = flow {
    val collection = this@liveDocuments
    val cached = runCatching { collection.get(Source.CACHE).await() }.getOrNull()
    val newest = cached?.documents?.mapNotNull { it.getTimestamp(UPDATED_AT)?.toDate()?.toInstant() }?.maxOrNull()
    val since = DeltaSync.cursor(newest, log.lastFullSync(path), log.now())
    val server: Query = if (since == null) collection else collection.whereGreaterThan(UPDATED_AT, Timestamp(since))
    val remote = server.snapshotFlow(MetadataChanges.INCLUDE)
        .retryAfterOwnWrites(db)
        .onEach { if (since == null && !it.metadata.isFromCache) log.markFullSync(path) }
    val local = collection.cacheSnapshotFlow()
    emitAll(
        combine(local, remote) { l, r ->
            LiveDocuments(l.documents.filterNot { with(Tombstones) { it.isTombstone() } }, r.metadata.isFromCache)
        },
    )
}

/**
 * The newest [limit] documents by [field], for collections that are only added to (Recent activity, after v1): the
 * cache listener shows the newest ones the phone has, and the server listener reads only documents newer than the
 * newest cached one (less [DeltaSync.CURSOR_MARGIN]), or the newest [limit] on the daily full read. Removals that
 * happen while a document is in the server listener's result (Undo) show at once; others (pruning, an Undo while
 * the phone was away) by the next full read at the latest.
 */
fun CollectionReference.liveNewest(
    db: FirebaseFirestore,
    log: SyncLog,
    field: String,
    limit: Long,
): Flow<LiveDocuments> = flow {
    val collection = this@liveNewest
    val newestQuery = collection.orderBy(field, Query.Direction.DESCENDING).limit(limit)
    val cached = runCatching { newestQuery.get(Source.CACHE).await() }.getOrNull()
    val newest = cached?.documents?.firstNotNullOfOrNull { it.getTimestamp(field)?.toDate()?.toInstant() }
    val since = DeltaSync.cursor(newest, log.lastFullSync(path), log.now())
    val server: Query = if (since == null) newestQuery else collection.whereGreaterThan(field, Timestamp(since))
    val remote = server.snapshotFlow(MetadataChanges.INCLUDE)
        .retryAfterOwnWrites(db)
        .onEach { if (since == null && !it.metadata.isFromCache) log.markFullSync(path) }
    emitAll(
        combine(newestQuery.cacheSnapshotFlow(), remote) { l, r -> LiveDocuments(l.documents, r.metadata.isFromCache) },
    )
}

/** A listener on the local cache only: free, and updated by local writes and by every other listener. */
private fun Query.cacheSnapshotFlow(): Flow<QuerySnapshot> = callbackFlow {
    val options = SnapshotListenOptions.Builder()
        .setMetadataChanges(MetadataChanges.INCLUDE)
        .setSource(ListenSource.CACHE)
        .build()
    val registration = addSnapshotListener(options) { snapshot, error ->
        when {
            error != null -> close(error.toAppError())
            snapshot != null -> trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}

private const val UPDATED_AT = "updatedAt"
