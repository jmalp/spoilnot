package com.parcha.spoilnot.core.firebase

import com.parcha.spoilnot.core.error.AppError
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A changes-only listener's server half when the quota runs out (after v1, REL-02): the list stays on screen from the
 * cache, marked offline, and the server listener starts again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeepCacheTest {
    /** A server listener that sends "from the server", then fails with [errors] one listen after another. */
    private class FakeServer(private val errors: List<Throwable>) {
        var listens = 0

        val flow = flow {
            val error = errors.getOrNull(listens++)
            emit(false)
            if (error != null) throw error
        }
    }

    @Test
    fun `a quota or network refusal reports the cache and listens again with a backoff`() = runTest {
        val server = FakeServer(listOf(AppError.QuotaExceeded(), AppError.QuotaExceeded(), AppError.Offline()))
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            server.flow.keepCacheWhileUnreachable().collect { seen += it }
        }
        assertEquals(listOf(false, true), seen)
        assertEquals(1, server.listens)

        advanceTimeBy(1_001)
        assertEquals(2, server.listens)
        advanceTimeBy(2_001)
        assertEquals(3, server.listens)
        advanceTimeBy(4_001)
        assertEquals(4, server.listens)
        // The fourth listen worked: the server's answer replaces the cache.
        assertEquals(listOf(false, true, false, true, false, true, false), seen)
    }

    @Test
    fun `a permission refusal or a bad document still ends the listener`() = runTest {
        for (error in listOf(AppError.PermissionDenied(), AppError.Unknown(IllegalStateException("bad field")))) {
            val result = runCatching { FakeServer(listOf(error)).flow.keepCacheWhileUnreachable().toList() }
            assertTrue(result.exceptionOrNull() === error)
        }
    }

    @Test
    fun `the backoff grows to 32 s`() {
        assertEquals(
            listOf(1L, 2L, 4L, 8L, 16L, 32L, 32L),
            (0L..6L).map { DeltaSync.retryDelay(it).seconds },
        )
        assertEquals(Duration.ofSeconds(32), DeltaSync.retryDelay(100))
    }
}
