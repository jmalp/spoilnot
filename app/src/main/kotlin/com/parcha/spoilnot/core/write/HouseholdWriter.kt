package com.parcha.spoilnot.core.write

import com.parcha.spoilnot.core.error.AppError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Who is making a change, for createdBy/updatedBy and the activity log. */
data class Actor(val uid: String, val name: String)

/** What an Undo puts back (DESIGN 5.2: every destructive action has Undo for 8 seconds). */
data class Undo(val changes: List<HouseholdChange>)

/** One write to household data. Features define their own changes (pantry, grocery) as plain data. */
interface HouseholdChange

/**
 * Applies plans of [HouseholdChange]s from any feature in batched writes (DESIGN 9.3), so a flow such as
 * "Add to pantry" or the restock engine changes several collections atomically.
 */
interface HouseholdWriter {
    /** A new random document id (Firestore auto ids are not tied to a collection). */
    fun newId(): String

    /**
     * Applies the changes in order, in as few batches as the 400-operation limit allows (DESIGN 8.5). Returns as
     * soon as they are applied to the local cache; a later rejection by the server arrives on [errors].
     */
    suspend fun apply(householdId: String, actor: Actor, changes: List<HouseholdChange>)

    val errors: Flow<AppError>

    /**
     * [apply] for a change nobody asked for, such as the pantry's clean-up of empty batches (DESIGN 8.5): a refusal
     * isn't reported on [errors]. [onAnswer] gets the server's answer, null when it accepted every batch, once it
     * comes (never while offline). Writers that can't tell (test fakes) answer at once.
     */
    suspend fun applyQuietly(
        householdId: String,
        actor: Actor,
        changes: List<HouseholdChange>,
        onAnswer: (AppError?) -> Unit,
    ) {
        apply(householdId, actor, changes)
        onAnswer(null)
    }

    /**
     * Every plan this phone applied, as soon as it is in the local cache. The restock engine uses it to tell a change
     * made on this phone from one that came from another phone (DESIGN 9.6). Writers that never report it (test
     * fakes) look like a phone that only receives changes.
     */
    val localPlans: Flow<List<HouseholdChange>> get() = emptyFlow()
}
