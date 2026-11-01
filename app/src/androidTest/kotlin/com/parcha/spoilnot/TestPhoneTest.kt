package com.parcha.spoilnot

import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every Firestore holds ConnectivityManager callbacks and Android allows about 100 per app, so phones that are not
 * released crash a full run of the two-phone tests (DESIGN 17). Counts this app's callbacks in
 * `dumpsys connectivity` while phones are opened and closed.
 */
@RunWith(AndroidJUnit4::class)
class TestPhoneTest {
    @Before
    fun setUp() = EmulatorSupport.requireEmulatorAndNetworkAccess()

    @Test
    fun closingAPhoneReleasesItsNetworkCallbacks() = runBlocking {
        val before = networkCallbacks()
        val phones = List(PHONES) { TestPhone("cleanup") }
        // Any call starts the Firestore client, which opens its gRPC channel and connectivity monitor.
        phones.forEach { it.firestore.current.waitForPendingWrites().await() }
        val open = awaitCallbacks { it >= before + PHONES }
        assertTrue("Opening $PHONES phones went from $before to $open callbacks", open >= before + PHONES)

        phones.forEach { it.close() }
        // Fewer than one left per phone, so unrelated callbacks coming and going cannot fail the test.
        val closed = awaitCallbacks { it < before + PHONES }
        assertTrue("Closing them went from $open to $closed callbacks (before: $before)", closed < before + PHONES)
    }

    private suspend fun awaitCallbacks(predicate: (Int) -> Boolean): Int {
        var count = networkCallbacks()
        withTimeoutOrNull(10_000) {
            while (!predicate(count)) {
                delay(200)
                count = networkCallbacks()
            }
        }
        return count
    }

    /** This app's rows in the "Network Requests" list of `dumpsys connectivity`. */
    private fun networkCallbacks(): Int {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dumpsys connectivity")
            .let { ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText() } }
        return output.lines().count { it.startsWith("  uid/pid:${Process.myUid()}/") }
    }

    private companion object {
        const val PHONES = 5
    }
}
