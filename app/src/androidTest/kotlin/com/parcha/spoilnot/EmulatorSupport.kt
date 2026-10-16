package com.parcha.spoilnot

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.parcha.spoilnot.core.firebase.ACCESS_LOCAL_NETWORK
import com.parcha.spoilnot.core.firebase.FirebaseEmulators
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.feature.auth.data.FirebaseAuthRepository
import com.parcha.spoilnot.feature.auth.data.FirestoreUserRepository
import com.parcha.spoilnot.feature.household.data.FirestoreHouseholdRepository
import com.parcha.spoilnot.feature.household.data.FirestoreLocalData
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue

/** Instrumented tests talk to the Firebase Emulator Suite only (DESIGN 7.4). Start it with ./scripts/emulators.sh. */
object EmulatorSupport {
    const val PASSWORD = "correct-horse-battery"

    fun requireEmulatorAndNetworkAccess() {
        assumeTrue("Only dev debug builds use the emulators", BuildConfig.FIREBASE_EMULATOR_HOST.isNotBlank())
        if (Build.VERSION.SDK_INT >= 37) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName,
                ACCESS_LOCAL_NETWORK,
            )
        }
    }

    fun uniqueEmail(prefix: String) = "$prefix-${UUID.randomUUID().toString().take(8)}@example.com"
}

/**
 * One simulated phone: its own FirebaseApp (so its own Auth user and Firestore cache) wired to the production
 * repositories and session manager, exactly as the app wires them.
 */
class TestPhone(private val name: String) : AutoCloseable {
    private val app: FirebaseApp = run {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appName = "$name-${UUID.randomUUID()}"
        FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, appName)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val firestore = FirestoreProvider(app, BuildConfig.FIREBASE_EMULATOR_HOST)
    private val auth = FirebaseAuth.getInstance(app).also {
        FirebaseEmulators.configureAuth(it, BuildConfig.FIREBASE_EMULATOR_HOST)
    }

    val authRepository = FirebaseAuthRepository(auth)
    val households = FirestoreHouseholdRepository(firestore)
    val sessionManager = SessionManager(
        authRepository,
        FirestoreUserRepository(firestore),
        households,
        FirestoreLocalData(firestore),
        scope,
    )

    val uid: String get() = checkNotNull(authRepository.currentUser).uid

    /** Every session state seen, for failure messages. */
    val history: MutableList<Session> = java.util.Collections.synchronizedList(mutableListOf())

    init {
        scope.launch { sessionManager.session.collect { history += it } }
    }

    suspend fun awaitSession(timeoutMs: Long = 15_000, predicate: (Session) -> Boolean): Session =
        withTimeoutOrNull(timeoutMs) { sessionManager.session.first(predicate) }
            ?: throw AssertionError(
                "$name: session still ${sessionManager.session.value} after $timeoutMs ms; saw $history",
            )

    /** Signs out and stops listening. The FirebaseApp is kept: Auth keeps internal references to deleted apps. */
    override fun close() {
        runCatching { auth.signOut() }
        scope.cancel()
    }
}
