package com.parcha.spoilnot.core.firebase

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenSource
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotListenOptions
import com.google.firebase.firestore.Source
import com.parcha.spoilnot.core.error.AppError
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await

/**
 * Cheaper household listeners (after v1, DESIGN 9.8). Firestore bills a listener that was away more than 30 minutes
 * for every document again, which was most of the free quota. Instead each collection gets two listeners:
 *
 * - a **cache** listener on the whole collection, which is free and shows what the phone has, including its own
 *   unsent writes and whatever the other listener brings in;
 * - a **server** listener on only the documents changed since the cursor (`updatedAt` after it), which costs one read
 *   per changed document (at least one per query). The cursor is the newest change this phone's server listener
 *   received for that collection ([SyncLog.newestSeen], after v1 2026-11-09), less [CURSOR_MARGIN]. It never comes
 *   from the cache: the phone's own writes and other queries put fresh server times there without anything having
 *   read the other members' changes before them (SYNC-01).
 *
 * Deleting therefore leaves a tombstone ([Tombstones]) that the server listener can see; the daily job removes
 * tombstones after [Tombstones.KEEP_DAYS]. Once a day, whenever the cache is empty and until a server snapshot has
 * brought a change, the server listener reads the whole collection instead, which also drops anything the phone
 * kept by mistake.
 */
object DeltaSync {
    /** A full read at least this often per collection and phone. */
    val FULL_SYNC_EVERY: Duration = Duration.ofHours(20)

    /**
     * Reread a little before the newest change received: a snapshot can show the phone's own confirmed write before
     * the server has sent another member's slightly older one.
     */
    val CURSOR_MARGIN: Duration = Duration.ofMinutes(5)

    /** The pause before a refused server listener listens again: 1, 2, 4 … 32 s (as `SessionManager`). */
    fun retryDelay(attempt: Long): Duration = Duration.ofSeconds(1L shl attempt.coerceIn(0, 5).toInt())

    /**
     * Where the server listener starts: null means read everything (no change received yet, no full read on record,
     * the last one is older than [FULL_SYNC_EVERY], or the clock moved back).
     */
    fun cursor(
        newestSeen: Instant?,
        lastFullSync: Instant?,
        now: Instant,
        margin: Duration = CURSOR_MARGIN,
    ): Instant? = when {
        newestSeen == null || lastFullSync == null -> null
        Duration.between(lastFullSync, now) >= FULL_SYNC_EVERY -> null
        lastFullSync.isAfter(now) -> null
        else -> newestSeen.minus(margin)
    }

    /**
     * The newest [field] time in a server listener's [snapshot], or null when there is nothing to learn from it: a
     * snapshot from the cache isn't what the server sent, and a document with unsent writes shows the phone's copy.
     */
    fun newestReceived(snapshot: QuerySnapshot, field: String): Instant? {
        if (snapshot.metadata.isFromCache) return null
        return snapshot.documents
            .filterNot { it.metadata.hasPendingWrites() }
            .mapNotNull { it.getTimestamp(field)?.toDate()?.toInstant() }
            .maxOrNull()
    }
}

/** Deleted household documents (after v1): the document stays, marked with `deletedAt`, until the daily job. */
object Tombstones {
    const val FIELD = "deletedAt"

    /** The reminder job deletes tombstones older than this; phones read everything at least daily. */
    const val KEEP_DAYS = 2L

    /** The fields that mark a document deleted, merged into it (rules require the stamp). */
    fun fields(actor: Actor): Map<String, Any> = mapOf(FIELD to FieldValue.serverTimestamp()) + Stamps.updated(actor)

    /**
     * Deleted means `deletedAt` holds a time, the phone's estimate included: until the server confirms an offline
     * delete, the default read gives null, and the document would stay on screen (DESIGN 9.8). The rules never allow
     * `deletedAt: null`, so Undo removes the field with a full write.
     */
    fun DocumentSnapshot.isTombstone(): Boolean = get(FIELD, ServerTimestampBehavior.ESTIMATE) != null
}

/**
 * Per collection (and meal window) on this phone: when it was last read in full, and the newest change its server
 * listener received, which is where the next changes-only read starts (local preference, not household data). Its
 * keys are collection paths, so household and list IDs, and it is cleared with the local data (DESIGN 12.2,
 * control 6). [margin] is [DeltaSync.CURSOR_MARGIN]; tests shorten it.
 */
class SyncLog(
    private val prefs: SharedPreferences,
    private val clock: Clock,
    private val margin: Duration = DeltaSync.CURSOR_MARGIN,
) {
    fun lastFullSync(path: String): Instant? = prefs.getLong(path, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    fun markFullSync(path: String) = prefs.edit().putLong(path, clock.millis()).apply()

    /** The newest change time a server snapshot of [key] brought (DESIGN 9.8), or null before the first one. */
    fun newestSeen(key: String): Instant? =
        prefs.getLong(SEEN_PREFIX + key, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    /** Records [at] for [key] if it is newer than what is on record; it never moves back. */
    @Synchronized
    fun markSeen(key: String, at: Instant) {
        val millis = at.toEpochMilli()
        if (millis > prefs.getLong(SEEN_PREFIX + key, 0L)) prefs.edit().putLong(SEEN_PREFIX + key, millis).apply()
    }

    /** Where the server listener for [key] starts; null reads everything ([DeltaSync.cursor]). */
    fun cursor(key: String): Instant? = DeltaSync.cursor(newestSeen(key), lastFullSync(key), now(), margin)

    /** Forgets every collection, so the next read of each is a full one. */
    fun clear() = prefs.edit().clear().apply()

    fun now(): Instant = clock.instant()

    private companion object {
        const val SEEN_PREFIX = "seen:"
    }
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
fun CollectionReference.liveDocuments(db: FirebaseFirestore, log: SyncLog): Flow<LiveDocuments> =
    liveDocuments(db, log, scope = this, key = path)

/**
 * [DeltaSync] for part of a collection (after v1: one week of the meal plan). [scope] decides which documents are
 * shown and what the full read reads; changes are still read for the whole collection (they are few, and land in
 * the cache for other windows). Each [key] has its own full-read record and cursor, so a week seen for the first
 * time is read in full.
 */
fun CollectionReference.liveDocuments(
    db: FirebaseFirestore,
    log: SyncLog,
    scope: Query,
    key: String,
): Flow<LiveDocuments> = flow {
    val collection = this@liveDocuments
    val since = if (scope.isCachedEmpty()) null else log.cursor(key)
    val server: Query = if (since == null) scope else collection.whereGreaterThan(UPDATED_AT, Timestamp(since))
    val remote = server.snapshotFlow(MetadataChanges.INCLUDE)
        .retryAfterOwnWrites(db)
        .onEach { log.recordServerSnapshot(key, it, UPDATED_AT, fullRead = since == null) }
        .map { it.metadata.isFromCache }
        .keepCacheWhileUnreachable()
    val local = scope.cacheSnapshotFlow()
    emitAll(
        combine(local, remote) { l, fromCache ->
            LiveDocuments(l.documents.filterNot { with(Tombstones) { it.isTombstone() } }, fromCache)
        },
    )
}

/**
 * The newest [limit] documents by [field], for collections that are only added to (Recent activity, after v1): the
 * cache listener shows the newest ones the phone has, and the server listener reads only documents newer than the
 * newest one it received before (less [DeltaSync.CURSOR_MARGIN]), or the newest [limit] on the daily full read.
 * Removals that happen while a document is in the server listener's result (Undo) show at once; others (pruning, an
 * Undo while the phone was away) by the next full read at the latest.
 */
fun CollectionReference.liveNewest(
    db: FirebaseFirestore,
    log: SyncLog,
    field: String,
    limit: Long,
): Flow<LiveDocuments> = flow {
    val collection = this@liveNewest
    val newestQuery = collection.orderBy(field, Query.Direction.DESCENDING).limit(limit)
    val since = if (newestQuery.isCachedEmpty()) null else log.cursor(path)
    val server: Query = if (since == null) newestQuery else collection.whereGreaterThan(field, Timestamp(since))
    val remote = server.snapshotFlow(MetadataChanges.INCLUDE)
        .retryAfterOwnWrites(db)
        .onEach { log.recordServerSnapshot(path, it, field, fullRead = since == null) }
        .map { it.metadata.isFromCache }
        .keepCacheWhileUnreachable()
    emitAll(
        combine(newestQuery.cacheSnapshotFlow(), remote) { l, fromCache -> LiveDocuments(l.documents, fromCache) },
    )
}

/**
 * The server listener's "from the cache" flag, kept alive when the server refuses it for the quota or can't be
 * reached (after v1, REL-02): it then reports the cache (so the screen keeps its list and says "Offline") and listens
 * again after 1, 2, 4 … 32 s. A refused listen reads nothing, so the retries cost no quota. Other errors (a
 * permission refusal, a bad document) still end the flow.
 */
internal fun Flow<Boolean>.keepCacheWhileUnreachable(): Flow<Boolean> = retryWhen { cause, attempt ->
    if (cause !is AppError.QuotaExceeded && cause !is AppError.Offline) return@retryWhen false
    emit(true)
    delay(DeltaSync.retryDelay(attempt).toMillis())
    true
}

/** Nothing in the cache for this query (or the cache can't be read): read it in full, whatever the log says. */
private suspend fun Query.isCachedEmpty(): Boolean =
    runCatching { get(Source.CACHE).await().isEmpty }.getOrDefault(true)

/** What a server listener's [snapshot] for [key] tells the log: a full read, and the newest change it brought. */
private fun SyncLog.recordServerSnapshot(key: String, snapshot: QuerySnapshot, field: String, fullRead: Boolean) {
    if (snapshot.metadata.isFromCache) return
    DeltaSync.newestReceived(snapshot, field)?.let { markSeen(key, it) }
    if (fullRead) markFullSync(key)
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
