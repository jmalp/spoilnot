package com.parcha.spoilnot.core.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.parcha.spoilnot.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the Firebase SDK singletons. Only repositories in feature data/ packages may inject these
 * (DESIGN 7.2); UI and domain code depend on repository interfaces instead.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides
    @Singleton
    fun firebaseClients(): FirebaseClients {
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        FirebaseEmulators.configure(auth, firestore, BuildConfig.FIREBASE_EMULATOR_HOST)
        return FirebaseClients(auth, firestore)
    }

    @Provides
    fun firebaseAuth(clients: FirebaseClients): FirebaseAuth = clients.auth

    @Provides
    fun firebaseFirestore(clients: FirebaseClients): FirebaseFirestore = clients.firestore
}

/** Both SDKs configured together, so emulator settings are applied exactly once and before first use. */
class FirebaseClients(val auth: FirebaseAuth, val firestore: FirebaseFirestore)
