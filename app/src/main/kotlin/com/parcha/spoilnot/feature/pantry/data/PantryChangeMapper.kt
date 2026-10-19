package com.parcha.spoilnot.feature.pantry.data

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
import com.parcha.spoilnot.feature.pantry.data.FirestorePantryRepository.Companion.TYPE_ADDED
import com.parcha.spoilnot.feature.pantry.data.FirestorePantryRepository.Companion.TYPE_DISCARDED
import com.parcha.spoilnot.feature.pantry.data.FirestorePantryRepository.Companion.activity
import com.parcha.spoilnot.feature.pantry.data.FirestorePantryRepository.Companion.batchFields
import com.parcha.spoilnot.feature.pantry.data.FirestorePantryRepository.Companion.productFields
import com.parcha.spoilnot.feature.pantry.data.FirestorePantryRepository.Companion.products
import com.parcha.spoilnot.feature.pantry.data.FirestorePantryRepository.Companion.stock
import com.parcha.spoilnot.feature.pantry.domain.PantryChange
import com.parcha.spoilnot.feature.pantry.domain.PantryEvent
import javax.inject.Inject

/** Firestore writes for [PantryChange]s. */
class PantryChangeMapper @Inject constructor() : FirestoreChangeMapper {
    override fun write(
        batch: WriteBatch,
        db: FirebaseFirestore,
        householdId: String,
        actor: Actor,
        change: HouseholdChange,
    ): Boolean {
        if (change !is PantryChange) return false
        val hid = householdId
        val stamp = Stamps.updated(actor)
        val created = Stamps.created(actor)
        when (change) {
            is PantryChange.SetRestockArmed ->
                batch.update(
                    db.document("${products(hid)}/${change.productId}"),
                    mapOf("restockArmed" to change.armed) + stamp,
                )

            // Aliases are written only here and by AddAlias (an array union), so edits never drop one.
            is PantryChange.CreateProduct ->
                batch.set(
                    db.document("${products(hid)}/${change.product.id}"),
                    productFields(change.product) + mapOf("aliases" to change.product.aliases) + created + stamp,
                )

            is PantryChange.AddAlias ->
                batch.update(
                    db.document("${products(hid)}/${change.productId}"),
                    mapOf("aliases" to FieldValue.arrayUnion(change.alias)) + stamp,
                )

            is PantryChange.UpdateProduct ->
                batch.set(
                    db.document("${products(hid)}/${change.product.id}"),
                    productFields(change.product) + stamp,
                    SetOptions.merge(),
                )

            is PantryChange.CreateBatch ->
                batch.set(db.document("${stock(hid)}/${change.batch.id}"), batchFields(change.batch) + created + stamp)

            is PantryChange.RestoreBatch -> batch.set(
                db.document("${stock(hid)}/${change.batch.id}"),
                batchFields(change.batch) + stamp + mapOf(
                    "createdAt" to (change.batch.createdAt?.let { Timestamp(it) } ?: FieldValue.serverTimestamp()),
                    "createdBy" to actor.uid,
                ),
            )

            is PantryChange.AddToBatch ->
                batch.update(
                    db.document("${stock(hid)}/${change.batchId}"),
                    mapOf("quantity" to FieldValue.increment(change.delta)) + stamp,
                )

            is PantryChange.UpdateBatch -> batch.update(
                db.document("${stock(hid)}/${change.batchId}"),
                mapOf(
                    "quantity" to change.edit.quantity,
                    "expiryDate" to change.edit.expiryDate?.toString(),
                    "dateType" to change.edit.dateType.wire,
                    "location" to change.edit.location.wire,
                    "note" to change.edit.note,
                ) + stamp,
            )

            is PantryChange.MarkOpened -> batch.update(
                db.document("${stock(hid)}/${change.batchId}"),
                mapOf(
                    "openedOn" to change.openedOn?.toString(),
                    "expiryDate" to change.expiryDate?.toString(),
                ) + stamp,
            )

            is PantryChange.SetOpenedShelfLife -> batch.update(
                db.document("${products(hid)}/${change.productId}"),
                mapOf("openedShelfLifeDays" to change.days) + stamp,
            )

            is PantryChange.SyncBatchProduct -> batch.update(
                db.document("${stock(hid)}/${change.batchId}"),
                mapOf("productName" to change.name, "unit" to change.unit.wire, "category" to change.category.wire) +
                    stamp,
            )

            // A tombstone, so other phones' change listeners see the deletion (DESIGN 9.8).
            is PantryChange.DeleteBatch ->
                batch.set(db.document("${stock(hid)}/${change.batchId}"), Tombstones.fields(actor), SetOptions.merge())

            is PantryChange.LogEvent -> batch.set(
                db.document("${activity(hid)}/${change.eventId}"),
                mapOf(
                    "type" to if (change.type == PantryEvent.Type.Added) TYPE_ADDED else TYPE_DISCARDED,
                    "actorUid" to actor.uid,
                    "actorName" to actor.name,
                    "productId" to change.productId,
                    "params" to
                        mapOf("name" to change.name, "quantity" to change.quantity, "reason" to change.reason?.wire),
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )

            is PantryChange.DeleteEvent -> batch.delete(db.document("${activity(hid)}/${change.eventId}"))
        }
        return true
    }
}
