package com.parcha.spoilnot.core.coroutines

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/**
 * One upstream per key, shared by everything that collects it (after v1, DESIGN 9.1): the listeners start with the
 * first collector and stop [stopTimeoutMs] after the last one leaves, so a screen that is left (or an app that goes
 * to the background) lets go of them, and a screen that comes back within that time finds them still running. The
 * work of the upstream (mapping snapshots) runs once, in [scope], whoever collects.
 *
 * A collector that arrives later gets the latest value at once; nothing is kept after the upstream stops, so a
 * value from an old listener never stands in for the new one. A failure reaches every collector as before (each
 * decides: retry, or an empty screen), and the next collection starts a fresh upstream.
 */
class SharedByKey<K : Any, T>(private val scope: CoroutineScope, private val stopTimeoutMs: Long = STOP_TIMEOUT_MS) {
    private val shared = ConcurrentHashMap<K, SharedFlow<Result<T>>>()

    /** The shared flow for [key]; [upstream] is called only when no shared flow for it is running. */
    fun get(key: K, upstream: () -> Flow<T>): Flow<T> = flow {
        val entry = shared.computeIfAbsent(key) { share(it, upstream) }
        emitAll(entry.map { it.getOrThrow() })
    }

    private fun share(key: K, upstream: () -> Flow<T>): SharedFlow<Result<T>> {
        lateinit var entry: SharedFlow<Result<T>>
        entry = flow { emitAll(upstream()) }
            .map { Result.success(it) }
            .catch { e ->
                // Forget this one, so the next collection (a retry) listens again instead of replaying the failure.
                shared.remove(key, entry)
                emit(Result.failure(e))
            }
            .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMs, replayExpirationMillis = 0), replay = 1)
        return entry
    }

    companion object {
        /** DESIGN 9.1: a listener stays 5 s after its last screen leaves. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
