package com.parcha.spoilnot.core.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings

/** Ports must match firebase/firebase.json. */
object FirebaseEmulators {
    const val AUTH_PORT = 9099
    const val FIRESTORE_PORT = 8080

    /** Firestore offline cache size (DESIGN 9.5). */
    const val CACHE_SIZE_BYTES = 100L * 1024 * 1024

    /**
     * Applies cache settings and, when [emulatorHost] is not blank, points both SDKs at the Emulator Suite.
     * Must run before the first Firestore or Auth call on these instances.
     */
    fun configure(auth: FirebaseAuth, firestore: FirebaseFirestore, emulatorHost: String) {
        if (emulatorHost.isNotBlank()) {
            auth.useEmulator(emulatorHost, AUTH_PORT)
            firestore.useEmulator(emulatorHost, FIRESTORE_PORT)
        }
        firestore.firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings { setSizeBytes(CACHE_SIZE_BYTES) })
        }
    }
}
