package com.parcha.spoilnot.core.coroutines

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One shared pantry flow per household (after v1, B09; DESIGN 9.1): every screen and the sheets share one pair of
 * listeners, which stop 5 s after the last one leaves, start fresh after that, and do their mapping once, in the
 * app's scope rather than on the collector's (main) thread.
 */
class SharedByKeyTest {
    /** A fake listener: counts starts, stops and how often each snapshot was mapped. */
    private class Listener {
        var starts = 0
        var stops = 0
        var mapped = 0
        val snapshots = MutableStateFlow(1)
        var context: String? = null

        fun flow(): Flow<Int> = snapshots
            .map {
                mapped++
                context = currentCoroutineContext()[CoroutineName]?.name
                it * 10
            }
            .onStart { starts++ }
            .onCompletion { stops++ }
    }

    private fun TestScope.shared() = SharedByKey<String, Int>(CoroutineScope(backgroundScope.coroutineContext))

    @Test
    fun `screens on the same household share one listener and its mapping`() = runTest {
        val listener = Listener()
        val shared = shared()
        val seen = mutableListOf<Int>()
        repeat(3) { shared.get("h1") { listener.flow() }.onEach { seen += it }.launchIn(backgroundScope) }
        runCurrent()
        listener.snapshots.value = 2
        runCurrent()
        assertEquals(1, listener.starts)
        assertEquals("each snapshot mapped once, not per screen", 2, listener.mapped)
        assertEquals(listOf(10, 10, 10, 20, 20, 20), seen.sorted())
    }

    @Test
    fun `the listener stops 5 s after the last screen leaves, and a screen back within that keeps it`() = runTest {
        val listener = Listener()
        val shared = shared()
        val home = shared.get("h1") { listener.flow() }.launchIn(backgroundScope)
        runCurrent()
        home.cancel()
        advanceTimeBy(4_000)
        val pantry = shared.get("h1") { listener.flow() }.launchIn(backgroundScope)
        runCurrent()
        assertEquals("kept for the tab switch", 1, listener.starts)
        assertEquals(0, listener.stops)
        pantry.cancel()
        advanceTimeBy(SharedByKey.STOP_TIMEOUT_MS - 1)
        assertEquals(0, listener.stops)
        advanceTimeBy(2)
        assertEquals("let go once nothing collects (EFF-01)", 1, listener.stops)
    }

    @Test
    fun `after it stopped, the next screen starts a fresh listener and never sees the old value`() = runTest {
        val listener = Listener()
        val shared = shared()
        shared.get("h1") { listener.flow() }.first()
        advanceTimeBy(SharedByKey.STOP_TIMEOUT_MS + 1)
        assertEquals(1, listener.stops)
        listener.snapshots.value = 7
        val first = shared.get("h1") { listener.flow() }.first()
        assertEquals("a new listener, with a new cursor (DESIGN 9.8)", 2, listener.starts)
        assertEquals(70, first)
    }

    @Test
    fun `each household has its own listener`() = runTest {
        val a = Listener()
        val b = Listener()
        val shared = shared()
        shared.get("h1") { a.flow() }.launchIn(backgroundScope)
        shared.get("h2") { b.flow() }.launchIn(backgroundScope)
        runCurrent()
        assertEquals(1, a.starts)
        assertEquals(1, b.starts)
    }

    @Test
    fun `a failure reaches every screen, and the next collection listens again`() = runTest {
        var attempts = 0
        val shared = shared()
        val upstream = {
            flow {
                if (attempts++ == 0) throw IllegalStateException("quota")
                emit(1)
                awaitCancellation()
            }
        }
        val errors = mutableListOf<Throwable>()
        repeat(2) {
            backgroundScope.launch { shared.get("h1", upstream).catch { errors += it }.collect { } }
        }
        runCurrent()
        assertEquals(2, errors.size)
        assertTrue(errors.all { it is IllegalStateException })
        assertEquals("a retry does not get the failure replayed", 1, shared.get("h1", upstream).first())
        assertEquals(2, attempts)
    }

    @Test
    fun `the mapping runs in the shared scope, not on the screen's thread`() = runTest {
        // PERF-02: in the app the shared scope is the application scope on Dispatchers.Default.
        val listener = Listener()
        val shared = SharedByKey<String, Int>(CoroutineScope(backgroundScope.coroutineContext + CoroutineName("app")))
        val onScreen = CoroutineScope(backgroundScope.coroutineContext + CoroutineName("main"))
        onScreen.launch { shared.get("h1") { listener.flow() }.collect { } }
        runCurrent()
        assertEquals("app", listener.context)
    }
}
