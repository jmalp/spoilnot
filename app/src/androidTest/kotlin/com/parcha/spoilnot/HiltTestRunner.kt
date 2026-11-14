package com.parcha.spoilnot

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.parcha.spoilnot.core.coroutines.ReleaseAppScopes
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Runs instrumented tests in Hilt's test application so tests can use the real dependency graph, cancels each test's
 * application scope after it ([ReleaseAppScopes], DESIGN 13.1), and leaves out the [OptInTests] nobody asked for.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        val listeners = listOfNotNull(arguments.getString(LISTENER), ReleaseAppScopes::class.java.name)
        arguments.putString(LISTENER, listeners.joinToString(","))
        val notAsked = OptInTests.byArgument.filterKeys { arguments.getString(it) != "true" }.values
        val excluded = listOfNotNull(arguments.getString(NOT_ANNOTATION)) + notAsked
        if (excluded.isNotEmpty()) arguments.putString(NOT_ANNOTATION, excluded.joinToString(","))
        super.onCreate(arguments)
    }

    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)

    private companion object {
        const val LISTENER = "listener"
        const val NOT_ANNOTATION = "notAnnotation"
    }
}
