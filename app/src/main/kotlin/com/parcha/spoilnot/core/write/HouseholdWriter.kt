package com.parcha.spoilnot.core.write

import com.parcha.spoilnot.core.error.AppError
import kotlinx.coroutines.flow.Flow

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
}
