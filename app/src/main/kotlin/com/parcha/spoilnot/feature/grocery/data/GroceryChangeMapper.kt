package com.parcha.spoilnot.feature.grocery.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.error.AppError.LostRace.Race
import com.parcha.spoilnot.core.firebase.FirestoreChangeMapper
import com.parcha.spoilnot.core.firebase.Stamps
import com.parcha.spoilnot.core.firebase.Tombstones
import com.parcha.spoilnot.core.firebase.awaitMapped
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.feature.grocery.data.FirestoreGroceryRepository.Companion.items
import com.parcha.spoilnot.feature.grocery.data.FirestoreGroceryRepository.Companion.lists
import com.parcha.spoilnot.feature.grocery.domain.GroceryChange
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryRules
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.pantry.domain.PantryChange
import javax.inject.Inject

/** Firestore writes for [GroceryChange]s. */
class GroceryChangeMapper @Inject constructor() : FirestoreChangeMapper {
    override fun write(
        batch: WriteBatch,
        db: FirebaseFirestore,
        householdId: String,
        actor: Actor,
        change: HouseholdChange,
    ): Boolean {
        if (change !is GroceryChange) return false
        val hid = householdId
        val stamp = Stamps.updated(actor)
        when (change) {
            is GroceryChange.CreateList -> batch.set(
                db.document("${lists(hid)}/${change.list.id}"),
                mapOf(
                    "name" to change.list.name,
                    "isDefault" to change.list.isDefault,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )

            is GroceryChange.RenameList ->
                batch.update(
                    db.document("${lists(hid)}/${change.listId}"),
                    mapOf(
                        "name" to change.name,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )

            is GroceryChange.SetAisleOrder ->
                batch.update(
                    db.document("${lists(hid)}/${change.listId}"),
                    mapOf("aisleOrder" to change.order.map { it.wire }, "updatedAt" to FieldValue.serverTimestamp()),
                )

            is GroceryChange.DeleteList -> batch.delete(db.document("${lists(hid)}/${change.listId}"))

            is GroceryChange.CreateItem ->
                batch.set(
                    item(db, hid, change.listId, change.item.id),
                    itemFields(change.item) + Stamps.created(actor) + stamp,
                )

            is GroceryChange.RestoreItem -> batch.set(
                item(db, hid, change.listId, change.item.id),
                itemFields(change.item) + stamp + mapOf(
                    "createdAt" to (change.item.createdAt?.let { Timestamp(it) } ?: FieldValue.serverTimestamp()),
                    "createdBy" to (change.item.createdBy ?: actor.uid),
                ),
            )

            is GroceryChange.UpdateItem -> batch.update(
                item(db, hid, change.listId, change.itemId),
                mapOf(
                    "quantity" to change.quantity,
                    "unit" to change.unit?.wire,
                    "note" to change.note,
                    "category" to change.category?.wire,
                ) + stamp,
            )

            is GroceryChange.RenameItem -> batch.update(
                item(db, hid, change.listId, change.itemId),
                mapOf("name" to change.name, "nameKey" to change.nameKey, "productId" to change.productId) + stamp,
            )

            is GroceryChange.AddToItem ->
                batch.update(
                    item(db, hid, change.listId, change.itemId),
                    mapOf("quantity" to FieldValue.increment(change.delta)) + stamp,
                )

            is GroceryChange.SetChecked -> batch.update(
                item(db, hid, change.listId, change.itemId),
                mapOf(
                    "checked" to change.checked,
                    "checkedBy" to if (change.checked) actor.uid else null,
                    "checkedAt" to if (change.checked) FieldValue.serverTimestamp() else null,
                ) + stamp,
            )

            // A tombstone, so other phones' change listeners see the deletion (DESIGN 9.8).
            is GroceryChange.DeleteItem -> if (change.tombstone) {
                batch.set(item(db, hid, change.listId, change.itemId), Tombstones.fields(actor), SetOptions.merge())
            } else {
                batch.delete(item(db, hid, change.listId, change.itemId))
            }

            is GroceryChange.LogEvent -> batch.set(
                db.document("households/$hid/activity/${change.eventId}"),
                mapOf(
                    "type" to change.type.wire,
                    "actorUid" to actor.uid,
                    "actorName" to actor.name,
                    "params" to mapOf("name" to change.name, "listName" to change.listName, "count" to change.count),
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )

            is GroceryChange.UpsertLowStock -> {
                // Merge-write. A live item keeps its `checked` (one already in the cart stays checked); a new item, or
                // a tombstone coming back (one left by Add to pantry is still checked), starts unchecked (DESIGN 9.6).
                // No createdBy: the rules would refuse a second phone's identical write naming another creator, so
                // two phones writing the same item at once both succeed (a tombstone keeps its old creator).
                val fields = mutableMapOf<String, Any?>(
                    "name" to change.name,
                    "nameKey" to change.nameKey,
                    "unit" to change.unit.wire,
                    "category" to change.category.wire,
                    "source" to ItemSource.LowStock.wire,
                    "productId" to change.productId,
                    "quantity" to change.quantity,
                    // Coming back after being removed: no longer a tombstone.
                    Tombstones.FIELD to FieldValue.delete(),
                ) + stamp
                val all = if (change.writeCreated) {
                    fields + mapOf(
                        "createdAt" to FieldValue.serverTimestamp(),
                        "note" to null,
                        "recipeId" to null,
                        "aliasOf" to null,
                        "checked" to false,
                        "checkedBy" to null,
                        "checkedAt" to null,
                    )
                } else {
                    fields
                }
                batch.set(
                    item(db, hid, change.listId, GroceryRules.lowStockItemId(change.productId)),
                    all,
                    SetOptions.merge(),
                )
            }
        }
        return true
    }

    /**
     * The rules refuse an item created under a deleted list, and a second tombstone on an item (DESIGN 8.5): Add to
     * pantry or a removal on two phones, or an add as another member deletes the list. Reads the list, then up to
     * [MAX_ITEMS_READ] of the removed items, from the server.
     */
    override suspend fun whyRefused(
        db: FirebaseFirestore,
        householdId: String,
        chunk: List<HouseholdChange>,
    ): AppError? {
        val addedTo = chunk.firstNotNullOfOrNull {
            when (it) {
                is GroceryChange.CreateItem -> it.listId
                is GroceryChange.RestoreItem -> it.listId
                else -> null
            }
        }
        if (addedTo != null && !listExists(db, householdId, addedTo)) return AppError.LostRace(Race.ListDeleted)
        val removed = chunk.filterIsInstance<GroceryChange.DeleteItem>().filter { it.tombstone }.take(MAX_ITEMS_READ)
        for (change in removed) {
            val doc = item(db, householdId, change.listId, change.itemId).get(Source.SERVER).awaitMapped()
            if (!doc.exists()) {
                if (!listExists(db, householdId, change.listId)) return AppError.LostRace(Race.ListDeleted)
                continue
            }
            if (with(Tombstones) { doc.isTombstone() }) {
                val race = if (chunk.any { it is PantryChange }) Race.AddedToPantry else Race.Removed
                return AppError.LostRace(race, doc.getString("updatedBy"))
            }
        }
        return null
    }

    private suspend fun listExists(db: FirebaseFirestore, hid: String, listId: String): Boolean =
        db.document("${lists(hid)}/$listId").get(Source.SERVER).awaitMapped().exists()

    private fun item(db: FirebaseFirestore, hid: String, listId: String, itemId: String) =
        db.document("${items(hid, listId)}/$itemId")

    /** Every field written explicitly, nulls included (DESIGN 8.1). */
    private fun itemFields(item: GroceryItem) = mapOf(
        "name" to item.name,
        "nameKey" to item.nameKey,
        "quantity" to item.quantity,
        "unit" to item.unit?.wire,
        "note" to item.note,
        "category" to item.category?.wire,
        "checked" to item.checked,
        "checkedBy" to item.checkedBy,
        "checkedAt" to null,
        "source" to item.source.wire,
        "productId" to item.productId,
        "recipeId" to item.recipeId,
        "aliasOf" to item.aliasOf,
    )

    private companion object {
        /** The items a refusal reads at most to find the one another phone removed first. */
        const val MAX_ITEMS_READ = 10
    }
}
