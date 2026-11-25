package com.parcha.spoilnot.core.coroutines

import com.parcha.spoilnot.core.error.AppError
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** DESIGN 6.7's "Couldn't load. Retry": a failed listener listens again instead of ending (after v1, REL-04). */
@OptIn(ExperimentalCoroutinesApi::class)
class LoadRetryTest {
    /** A listener that fails its first [failures] listens, then emits "ok". */
    private fun listener(failures: Int, listens: MutableList<Int>): Flow<String> = flow {
        listens += listens.size
        if (listens.size <= failures) throw IOException("refused")
        emit("ok")
        // A listener stays open.
        awaitCancellation()
    }

    @Test
    fun `a failure shows the error, listens again after 1, 2, 4 s, and clears when data comes`() = runTest {
        val load = LoadRetry()
        val listens = mutableListOf<Int>()
        val values = mutableListOf<String>()
        val failed = mutableListOf<Boolean>()
        load.failed.onEach { failed += it }.launchIn(backgroundScope)
        listener(failures = 3, listens).retryLoading(load).onEach { values += it }.launchIn(backgroundScope)
        runCurrent()
        assertEquals(1, listens.size)
        assertEquals(listOf(false, true), failed)
        advanceTimeBy(1_001)
        assertEquals(2, listens.size)
        advanceTimeBy(2_001)
        assertEquals(3, listens.size)
        advanceTimeBy(4_001)
        assertEquals(4, listens.size)
        assertEquals(listOf("ok"), values)
        // The message goes once the listen has stayed up for 2 s.
        assertEquals(listOf(false, true), failed)
        advanceTimeBy(2_001)
        assertEquals(listOf(false, true, false), failed)
    }

    @Test
    fun `a listen that shows the cache and fails again keeps the message without flickering`() = runTest {
        val load = LoadRetry()
        val failed = mutableListOf<Boolean>()
        load.failed.onEach { failed += it }.launchIn(backgroundScope)
        // As a refused listener does: the phone's copy first, then the refusal.
        val listener = flow {
            emit("cached")
            delay(10)
            throw IOException("refused")
        }
        listener.retryLoading(load).launchIn(backgroundScope)
        advanceTimeBy(30_000)
        assertEquals(listOf(false, true), failed)
    }

    @Test
    fun `Retry listens again at once`() = runTest {
        val load = LoadRetry()
        val listens = mutableListOf<Int>()
        val values = mutableListOf<String>()
        listener(failures = 5, listens).retryLoading(load).onEach { values += it }.launchIn(backgroundScope)
        runCurrent()
        repeat(5) {
            load.retry()
            runCurrent()
        }
        assertEquals(6, listens.size)
        assertEquals(listOf("ok"), values)
    }

    @Test
    fun `a fallback is emitted once when the first listen fails`() = runTest {
        val listens = mutableListOf<Int>()
        val values = mutableListOf<String>()
        listener(failures = 2, listens).retryLoading(fallback = "none").onEach {
            values += it
        }.launchIn(backgroundScope)
        advanceTimeBy(10_000)
        assertEquals(listOf("none", "ok"), values)
    }

    @Test
    fun `the quota and the network don't say Couldn't load, which have their own banners, but retry`() = runTest {
        val load = LoadRetry()
        val failed = mutableListOf<Boolean>()
        load.failed.onEach { failed += it }.launchIn(backgroundScope)
        var listens = 0
        flow<String> {
            listens++
            throw if (listens % 2 == 0) AppError.Offline() else AppError.QuotaExceeded()
        }.retryLoading(load).launchIn(backgroundScope)
        advanceTimeBy(3_500)
        assertEquals(3, listens)
        assertEquals(listOf(false), failed)
    }

    @Test
    fun `the wait grows to 32 s and no further`() {
        assertEquals(listOf(1L, 2, 4, 8, 16, 32, 32), (0L..6L).map { LoadRetry.delay(it).seconds })
    }
}
