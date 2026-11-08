package com.parcha.spoilnot

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.parcha.spoilnot.core.coroutines.ReleaseAppScopes
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Runs instrumented tests in Hilt's test application so tests can use the real dependency graph, and cancels each
 * test's application scope after it ([ReleaseAppScopes], DESIGN 13.1).
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        val listeners = listOfNotNull(arguments.getString(LISTENER), ReleaseAppScopes::class.java.name)
        arguments.putString(LISTENER, listeners.joinToString(","))
        super.onCreate(arguments)
    }

    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)

    private companion object {
        const val LISTENER = "listener"
    }
}
