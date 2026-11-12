package com.parcha.spoilnot.feature.pantry.domain

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which empty batches this process is cleaning up or may not clean up (DESIGN 8.5, after v1, review REL-11): one whose
 * clean-up is waiting for the server isn't written again, and one the server refused isn't tried again until the app
 * restarts. Another phone, or a fixed batch (its quantity back above 0), still cleans it up normally.
 */
@Singleton
class EmptyBatchCleanups @Inject constructor() {
    private val waiting = mutableSetOf<String>()
    private val refused = mutableSetOf<String>()

    /** The batch IDs of [ids] to clean up now, marked as waiting. */
    @Synchronized
    fun toTry(ids: List<String>): List<String> = ids.filter { it !in waiting && it !in refused }.also { waiting += it }

    /** The server answered the clean-up of [ids]; [refused] remembers them for this process. */
    @Synchronized
    fun answered(ids: List<String>, refused: Boolean) {
        waiting -= ids.toSet()
        if (refused) this.refused += ids
    }
}
