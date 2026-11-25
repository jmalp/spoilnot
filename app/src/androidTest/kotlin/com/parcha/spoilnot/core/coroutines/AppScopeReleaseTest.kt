package com.parcha.spoilnot.core.coroutines

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.feature.household.domain.SessionManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * A Hilt test's session must not outlive it: a `SessionManager` left running watches the shared FirebaseAuth and
 * Firestore and once terminated the Firestore of a later test (DESIGN 13.1, 17). The first test starts a session
 * in its application scope; the second, run right after it, finds that scope cancelled and its own the only one
 * alive.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppScopeReleaseTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var sessionManager: SessionManager

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        EmulatorSupport.requireEmulatorAndNetworkAccess()
        hilt.inject()
    }

    @Test
    fun a_startsASessionInTheApplicationScope() {
        val job = scope.coroutineContext.job
        sessionManager.session.value
        assertTrue("The session watcher runs in the application scope", job.children.any())
        assertEquals("Only this test's scope is alive", listOf(job), TestAppScopes.live())
        previous = job
    }

    @Test
    fun b_findsThePreviousTestsScopeCancelled() {
        val earlier = previous
        assumeTrue("Runs right after a_startsASessionInTheApplicationScope", earlier != null)
        checkNotNull(earlier)
        assertTrue("The previous test's scope was cancelled", earlier.isCancelled)
        assertTrue("Its coroutines have stopped", earlier.isCompleted)
        val job = scope.coroutineContext.job
        assertNotSame(earlier, job)
        assertEquals("Only this test's scope is alive", listOf(job), TestAppScopes.live())
    }

    private companion object {
        var previous: Job? = null
    }
}
