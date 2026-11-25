package com.parcha.spoilnot.core.coroutines

import com.parcha.spoilnot.core.error.AppError
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * DESIGN 6.7's "Couldn't load. Retry" for one screen (after v1, review REL-04). Its listeners go through
 * [retryLoading]: one that fails listens again after 1, 2, 4 … 32 s, or at once when the person taps Retry, instead
 * of ending, which left the screen blank until the app restarted. [failed] is true while any of them is failing.
 */
class LoadRetry {
    private val failing = MutableStateFlow<Set<Any>>(emptySet())
    private val now = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** True while one of this screen's listeners has failed and hasn't brought anything since. */
    val failed: Flow<Boolean> = failing.map { it.isNotEmpty() }.distinctUntilChanged()

    /** Retry: listens again at once instead of at the end of the wait. */
    fun retry() {
        now.tryEmit(Unit)
    }

    internal fun fail(token: Any) = failing.update { it + token }

    internal fun recover(token: Any) {
        if (token in failing.value) failing.update { it - token }
    }

    internal suspend fun await(attempt: Long) {
        withTimeoutOrNull(delay(attempt).toMillis()) { now.first() }
    }

    companion object {
        /** 1, 2, 4 … 32 s, as the session and the changes-only listeners (DESIGN 7.5). */
        fun delay(attempt: Long): Duration = Duration.ofSeconds(1L shl attempt.coerceIn(0, 5).toInt())

        /** How long a listener must stay up after a failure before "Couldn't load" goes. */
        const val RECOVERED_AFTER_MS = 2_000L
    }
}

/**
 * Listens again after a failure (DESIGN 6.7) instead of ending. With [load] the screen says "Couldn't load. Retry"
 * meanwhile and Retry listens again at once; without it the retry is silent (a secondary list, such as the members
 * who name an author). After the first value the last one stays while it retries; the message goes once a listen has
 * stayed up for [LoadRetry.RECOVERED_AFTER_MS].
 */
fun <T> Flow<T>.retryLoading(load: LoadRetry? = null): Flow<T> = retrying(load, hasFallback = false, fallback = null)

/** [retryLoading] that emits [fallback] once when the very first listen fails, so screens combining it still show. */
fun <T> Flow<T>.retryLoading(load: LoadRetry? = null, fallback: T): Flow<T> =
    retrying(load, hasFallback = true, fallback)

private fun <T> Flow<T>.retrying(load: LoadRetry?, hasFallback: Boolean, fallback: T?): Flow<T> = channelFlow {
    val token = Any()
    var emitted = false
    var attempt = 0L
    var recovering: Job? = null
    try {
        while (true) {
            try {
                collect { value ->
                    emitted = true
                    send(value)
                    // A listen that comes back shows the phone's copy first, then may fail again at once: the
                    // message goes only once it has stayed up a moment, so it doesn't flicker on every retry.
                    if (recovering?.isActive != true) {
                        recovering = launch {
                            delay(LoadRetry.RECOVERED_AFTER_MS)
                            attempt = 0
                            load?.recover(token)
                        }
                    }
                }
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recovering?.cancel()
                Timber.w(e, "A listener failed; listening again")
                // The quota and the network have their own banners (DESIGN 6.7); they still retry.
                if (e !is AppError.QuotaExceeded && e !is AppError.Offline) load?.fail(token)
                if (!emitted && hasFallback) {
                    emitted = true
                    @Suppress("UNCHECKED_CAST")
                    send(fallback as T)
                }
                if (load != null) load.await(attempt) else delay(LoadRetry.delay(attempt).toMillis())
                attempt++
            }
        }
    } finally {
        recovering?.cancel()
        load?.recover(token)
    }
}
