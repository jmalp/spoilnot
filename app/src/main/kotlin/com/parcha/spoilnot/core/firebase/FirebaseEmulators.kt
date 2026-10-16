package com.parcha.spoilnot.core.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import java.util.Collections
import java.util.WeakHashMap

/** Ports must match firebase/firebase.json. */
object FirebaseEmulators {
    const val AUTH_PORT = 9099
    const val FIRESTORE_PORT = 8080

    /** Firestore offline cache size (DESIGN 9.5). */
    const val CACHE_SIZE_BYTES = 100L * 1024 * 1024

    // Settings can be applied only once per instance, before first use. Instances outlive a Hilt component in
    // instrumented tests, so remember which ones are already configured.
    private val configured: MutableSet<Any> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    /** Points Auth at the emulator when [emulatorHost] is not blank. Safe to call more than once. */
    fun configureAuth(auth: FirebaseAuth, emulatorHost: String) {
        if (!configured.add(auth)) return
        if (emulatorHost.isNotBlank()) auth.useEmulator(emulatorHost, AUTH_PORT)
    }

    /**
     * Applies cache settings and, when [emulatorHost] is not blank, the emulator. Call on every new instance before
     * first use (a fresh instance is created after each local-data reset). Safe to call more than once.
     */
    fun configureFirestore(firestore: FirebaseFirestore, emulatorHost: String) {
        if (!configured.add(firestore)) return
        if (emulatorHost.isNotBlank()) firestore.useEmulator(emulatorHost, FIRESTORE_PORT)
        firestore.firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings { setSizeBytes(CACHE_SIZE_BYTES) })
        }
    }
}
