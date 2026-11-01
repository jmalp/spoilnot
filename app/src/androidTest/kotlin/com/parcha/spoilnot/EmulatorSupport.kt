package com.parcha.spoilnot

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.parcha.spoilnot.core.firebase.ACCESS_LOCAL_NETWORK
import com.parcha.spoilnot.core.firebase.FirebaseEmulators
import com.parcha.spoilnot.core.firebase.FirestoreHouseholdWriter
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.core.firebase.SyncLog
import com.parcha.spoilnot.core.prefs.AppPreferences
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.feature.auth.data.FirebaseAuthRepository
import com.parcha.spoilnot.feature.auth.data.FirestoreUserRepository
import com.parcha.spoilnot.feature.grocery.data.FirestoreGroceryRepository
import com.parcha.spoilnot.feature.grocery.data.GroceryChangeMapper
import com.parcha.spoilnot.feature.grocery.domain.GroceryService
import com.parcha.spoilnot.feature.grocery.domain.RestockRunner
import com.parcha.spoilnot.feature.home.data.FirestoreActivityRepository
import com.parcha.spoilnot.feature.household.data.FirestoreHouseholdRepository
import com.parcha.spoilnot.feature.household.data.FirestoreLocalData
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.insights.data.FirestoreStatsRepository
import com.parcha.spoilnot.feature.insights.data.StatsChangeMapper
import com.parcha.spoilnot.feature.notifications.domain.DeviceRegistry
import com.parcha.spoilnot.feature.pantry.data.FirestorePantryRepository
import com.parcha.spoilnot.feature.pantry.data.PantryChangeMapper
import com.parcha.spoilnot.feature.pantry.domain.PantryService
import com.parcha.spoilnot.feature.plan.data.FirestoreMealPlanRepository
import com.parcha.spoilnot.feature.plan.data.MealPlanChangeMapper
import com.parcha.spoilnot.feature.plan.domain.MealPlanService
import com.parcha.spoilnot.feature.recipes.data.FirestoreRecipeRepository
import com.parcha.spoilnot.feature.recipes.data.RecipeChangeMapper
import com.parcha.spoilnot.feature.recipes.domain.RecipeService
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue

/** Instrumented tests talk to the Firebase Emulator Suite only (DESIGN 7.4). Start it with ./scripts/emulators.sh. */
object EmulatorSupport {
    const val PASSWORD = "correct-horse-battery"

    fun requireEmulatorAndNetworkAccess() {
        assumeTrue("Only dev debug builds use the emulators", BuildConfig.FIREBASE_EMULATOR_HOST.isNotBlank())
        skipAppTour()
        if (Build.VERSION.SDK_INT >= 37) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName,
                ACCESS_LOCAL_NETWORK,
            )
        }
    }

    /** UI tests start without the app tour over the screen (DESIGN 5.8); `TourUiTest` turns it on. */
    fun skipAppTour(done: Boolean = true) = runBlocking {
        AppPreferences(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext).setTourDone(done)
    }

    fun uniqueEmail(prefix: String) = "$prefix-${UUID.randomUUID().toString().take(8)}@example.com"
}

/**
 * One simulated phone: its own FirebaseApp (so its own Auth user and Firestore cache) wired to the production
 * repositories and session manager, exactly as the app wires them. [close] releases its Firestore after each test.
 */
class TestPhone(private val name: String) : AutoCloseable {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app: FirebaseApp =
        FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, "$name-${UUID.randomUUID()}")
    private val syncLogPrefs = "delta_sync_${app.name}"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val firestore = FirestoreProvider(app, BuildConfig.FIREBASE_EMULATOR_HOST)
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
        // Emulator builds cannot register for push; device sync is covered by the live test (DESIGN 14 M4).
        object : DeviceRegistry {
            override suspend fun sync(uid: String) = Unit

            override suspend fun unregister(uid: String) = Unit
        },
        scope,
    )

    val writer = FirestoreHouseholdWriter(
        firestore,
        scope,
        setOf(
            PantryChangeMapper(),
            GroceryChangeMapper(),
            RecipeChangeMapper(),
            StatsChangeMapper(),
            MealPlanChangeMapper(),
        ),
    )

    /** Each phone has its own record of full reads, as separate phones would (DESIGN 9.8). */
    val syncLog = SyncLog(
        context.getSharedPreferences(syncLogPrefs, android.content.Context.MODE_PRIVATE),
        Clock.systemUTC(),
    )
    val pantryRepository = FirestorePantryRepository(firestore, syncLog)
    val pantry = PantryService(writer, Clock.systemDefaultZone())
    val groceryRepository = FirestoreGroceryRepository(firestore, syncLog)
    val grocery = GroceryService(writer, groceryRepository, pantry)
    val recipeRepository = FirestoreRecipeRepository(firestore, syncLog)
    val recipes = RecipeService(writer)
    val stats = FirestoreStatsRepository(firestore)
    val activity = FirestoreActivityRepository(firestore, syncLog)
    val mealPlanRepository = FirestoreMealPlanRepository(firestore, syncLog)
    val mealPlan = MealPlanService(writer, pantry, recipes)

    /** Every phone runs the restock engine (DESIGN 9.6). Started by [startRestock]. */
    private val restock =
        RestockRunner(sessionManager, pantryRepository, groceryRepository, writer, scope, Clock.systemDefaultZone())

    fun startRestock() = restock.start()

    val uid: String get() = checkNotNull(authRepository.currentUser).uid

    val actor: Actor get() = Actor(uid, (sessionManager.session.value as? Session.InHousehold)?.displayName ?: "Test")

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

    /**
     * Losing a household clears local data in the background (DESIGN 12.2), which terminates the Firestore instance
     * a read could still be waiting on. Waits until [before] has been replaced by a fresh instance.
     */
    suspend fun awaitLocalDataCleared(before: FirebaseFirestore, timeoutMs: Long = 15_000) {
        withTimeoutOrNull(timeoutMs) { firestore.instance.first { it !== before } }
            ?: throw AssertionError("$name: local data still not cleared after $timeoutMs ms")
    }

    /**
     * Stops listening, then terminates Firestore before signing out: its gRPC channel and connectivity monitor
     * each hold a ConnectivityManager callback, and Android allows about 100 per app, so phones that are never
     * terminated crash a full test run. Then deletes the cache and sync log and signs out. The FirebaseApp is kept:
     * Auth's shared emulator client keeps the last app it configured, so deleting apps makes `useEmulator` on the
     * next one throw "FirebaseApp was deleted".
     */
    override fun close() {
        runBlocking {
            try {
                withTimeout(CLOSE_TIMEOUT_MS) {
                    scope.coroutineContext.job.cancelAndJoin()
                    val db = firestore.current
                    db.terminate().await()
                    db.clearPersistence().await()
                }
            } finally {
                runCatching { auth.signOut() }
                context.deleteSharedPreferences(syncLogPrefs)
            }
        }
    }

    private companion object {
        const val CLOSE_TIMEOUT_MS = 10_000L
    }
}
