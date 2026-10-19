package com.parcha.spoilnot.feature.grocery.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.parcha.spoilnot.core.firebase.FirestoreChangeMapper
import com.parcha.spoilnot.core.firebase.Stamps
import com.parcha.spoilnot.core.firebase.Tombstones
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.feature.grocery.data.FirestoreGroceryRepository.Companion.items
import com.parcha.spoilnot.feature.grocery.data.FirestoreGroceryRepository.Companion.lists
import com.parcha.spoilnot.feature.grocery.domain.GroceryChange
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryRules
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
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
                // Merge-write with no `checked` field: an item already in the cart stays checked (DESIGN 9.6).
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
                    fields + Stamps.created(actor) + mapOf("note" to null, "recipeId" to null)
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
    )
}
