package com.parcha.spoilnot.core.coroutines

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.runner.Description
import org.junit.runner.notification.RunListener

/**
 * Each Hilt test gets a new SingletonComponent, but nothing cancels the old one's application scope, so its
 * `SessionManager` would keep watching the shared default FirebaseAuth and Firestore and react to later tests (for
 * example by clearing local data, which terminates the Firestore the next test uses). The test application scope is
 * the app's, recorded in [TestAppScopes] so [ReleaseAppScopes] can cancel it after every test.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [CoroutinesModule::class])
object TestCoroutinesModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = TestAppScopes.create()
}

/** Every application scope a test component has made and not yet released. */
object TestAppScopes {
    private val scopes = CopyOnWriteArrayList<CoroutineScope>()

    fun create(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }

    /** The jobs of the scopes still alive: at most the running test's. */
    fun live(): List<Job> = scopes.map { it.coroutineContext.job }.filter { it.isActive }

    /** Cancels every scope and waits for its coroutines (session watchers, writers, device sync) to stop. */
    fun releaseAll() {
        val released = scopes.toList()
        scopes.removeAll(released.toSet())
        runBlocking {
            released.forEach { scope ->
                val stopped = withTimeoutOrNull(RELEASE_TIMEOUT_MS) { scope.coroutineContext.job.cancelAndJoin() }
                if (stopped == null) Log.w(TAG, "An application scope was still stopping after $RELEASE_TIMEOUT_MS ms")
            }
        }
    }

    private const val TAG = "TestAppScopes"
    private const val RELEASE_TIMEOUT_MS = 10_000L
}

/**
 * Added to every instrumented run by `HiltTestRunner`, so no test can forget it: after each test (and its rules,
 * including the activity closing) the application scopes are cancelled and no session is left behind.
 */
class ReleaseAppScopes : RunListener() {
    override fun testFinished(description: Description) = TestAppScopes.releaseAll()
}
