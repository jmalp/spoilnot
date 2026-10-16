package com.parcha.spoilnot.core.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.parcha.spoilnot.BuildConfig
import com.parcha.spoilnot.EmulatorSupport
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves dev debug builds reach the Firebase Emulator Suite, never real Firebase (DESIGN 7.4).
 * Needs the emulators running on the host: ./scripts/emulators.sh
 */
@RunWith(AndroidJUnit4::class)
class FirebaseEmulatorTest {
    @Test
    fun authEmulatorCreatesAndSignsInAUser() {
        val email = "m0-${UUID.randomUUID()}@example.com"
        val result = await(auth.createUserWithEmailAndPassword(email, "correct-horse-battery"))
        assertNotNull(result.user?.uid)
        assertEquals(email, auth.currentUser?.email)
    }

    @Test
    fun firestoreEmulatorEnforcesRules() {
        val error = try {
            await(firestore.document("households/not-a-member").get(Source.SERVER))
            null
        } catch (e: ExecutionException) {
            e.cause as? FirebaseFirestoreException
        }
        // PERMISSION_DENIED (not UNAVAILABLE) shows the emulator answered with the rules loaded.
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    private fun <T> await(task: Task<T>): T = Tasks.await(task, 20, TimeUnit.SECONDS)

    companion object {
        private lateinit var auth: FirebaseAuth
        private lateinit var firestore: FirebaseFirestore

        @JvmStatic
        @BeforeClass
        fun connect() {
            EmulatorSupport.requireEmulatorAndNetworkAccess()
            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()
            FirebaseEmulators.configureAuth(auth, BuildConfig.FIREBASE_EMULATOR_HOST)
            FirebaseEmulators.configureFirestore(firestore, BuildConfig.FIREBASE_EMULATOR_HOST)
        }
    }
}
