package com.parcha.spoilnot.core.firebase

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.parcha.spoilnot.BuildConfig
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
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
    fun firestoreEmulatorEnforcesDenyAllRules() {
        val error = try {
            await(firestore.document("config/app").get(Source.SERVER))
            null
        } catch (e: ExecutionException) {
            e.cause as? FirebaseFirestoreException
        }
        // PERMISSION_DENIED (not UNAVAILABLE) shows the emulator answered with the M0 rules loaded.
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    private fun <T> await(task: Task<T>): T = Tasks.await(task, 20, TimeUnit.SECONDS)

    companion object {
        private lateinit var auth: FirebaseAuth
        private lateinit var firestore: FirebaseFirestore

        @JvmStatic
        @BeforeClass
        fun connect() {
            assumeTrue("Only dev debug builds use the emulators", BuildConfig.FIREBASE_EMULATOR_HOST.isNotBlank())
            if (Build.VERSION.SDK_INT >= 37) {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                instrumentation.uiAutomation.grantRuntimePermission(
                    instrumentation.targetContext.packageName,
                    ACCESS_LOCAL_NETWORK,
                )
            }
            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()
            FirebaseEmulators.configure(auth, firestore, BuildConfig.FIREBASE_EMULATOR_HOST)
        }
    }
}
