package com.parcha.spoilnot.core.firebase

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Owns the Firestore instance. Clearing local data (sign-out, leaving or losing a household) terminates the
 * instance and clears its cache (DESIGN 12.2, control 6), which makes it unusable, so repositories always read
 * [current] or collect [instance] instead of keeping a reference.
 */
class FirestoreProvider(private val app: FirebaseApp, private val emulatorHost: String) {
    private val mutex = Mutex()
    private val _instance = MutableStateFlow(create())

    val instance: StateFlow<FirebaseFirestore> = _instance.asStateFlow()

    val current: FirebaseFirestore get() = _instance.value

    /** Sends pending writes if it can, then terminates, clears the cache and starts a fresh instance. */
    suspend fun clearLocalData() = mutex.withLock {
        val old = _instance.value
        val flushed = withTimeoutOrNull(PENDING_WRITES_TIMEOUT_MS) { old.waitForPendingWrites().await() }
        if (flushed == null) Timber.w("Clearing local data with unsent writes")
        old.terminate().await()
        old.clearPersistence().await()
        _instance.value = create()
    }

    private fun create(): FirebaseFirestore =
        FirebaseFirestore.getInstance(app).also { FirebaseEmulators.configureFirestore(it, emulatorHost) }

    private companion object {
        const val PENDING_WRITES_TIMEOUT_MS = 5_000L
    }
}
