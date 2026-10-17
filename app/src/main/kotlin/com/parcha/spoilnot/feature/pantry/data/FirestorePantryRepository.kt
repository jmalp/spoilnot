package com.parcha.spoilnot.feature.pantry.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.parcha.spoilnot.core.coroutines.ApplicationScope
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.core.firebase.awaitMapped
import com.parcha.spoilnot.core.firebase.retryAfterOwnWrites
import com.parcha.spoilnot.core.firebase.snapshotFlow
import com.parcha.spoilnot.feature.pantry.domain.Actor
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryChange
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryEvent
import com.parcha.spoilnot.feature.pantry.domain.PantryLimits
import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import com.parcha.spoilnot.feature.pantry.domain.PantryWriteErrors
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestorePantryRepository @Inject constructor(
    private val firestore: FirestoreProvider,
    @param:ApplicationScope private val scope: CoroutineScope,
) : PantryRepository,
    PantryWriteErrors {
    private val _errors = MutableSharedFlow<AppError>(extraBufferCapacity = 8)
    override val errors: SharedFlow<AppError> = _errors

    override fun observe(householdId: String): Flow<PantryData> = firestore.instance.flatMapLatest { db ->
        val products = db.collection(
            products(householdId),
        ).snapshotFlow(MetadataChanges.INCLUDE).retryAfterOwnWrites(db)
        // Metadata changes too, so each batch knows whether it still has unsent writes (pending-sync icon).
        val stock = db.collection(stock(householdId)).snapshotFlow(MetadataChanges.INCLUDE).retryAfterOwnWrites(db)
        combine(products, stock) { p, s ->
            PantryData(
                products = p.documents.map { it.toProduct() },
                batches = s.documents.map { it.toBatch() },
                fromCache = p.metadata.isFromCache || s.metadata.isFromCache,
            )
        }
    }.distinctUntilChanged()

    override fun observeEvents(householdId: String, productId: String, limit: Int): Flow<List<PantryEvent>> =
        firestore.instance.flatMapLatest { db ->
            // Equality only, so the automatic single-field index serves it (DESIGN 8.4); sorted here.
            db.collection(
                activity(householdId),
            ).whereEqualTo("productId", productId).snapshotFlow().retryAfterOwnWrites(db)
        }.map { snapshot ->
            snapshot.documents.mapNotNull { it.toEvent() }
                .sortedWith(compareByDescending(nullsFirst()) { it.createdAt })
                .take(limit)
        }.distinctUntilChanged()

    override fun newProductId(householdId: String): String =
        firestore.current.collection(products(householdId)).document().id

    override fun newBatchId(householdId: String): String =
        firestore.current.collection(stock(householdId)).document().id

    override fun newEventId(householdId: String): String =
        firestore.current.collection(activity(householdId)).document().id

    /**
     * Applies locally at once and returns: the cache gives the optimistic UI and offline support (DESIGN 9.3, 9.5).
     * A rejection by the server arrives later on [errors].
     */
    override suspend fun apply(householdId: String, actor: Actor, changes: List<PantryChange>) {
        val db = firestore.current
        changes.chunked(PantryLimits.BATCH_LIMIT).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.add(db, householdId, actor, it) }
            val task = batch.commit()
            scope.launch {
                try {
                    task.awaitMapped()
                } catch (e: AppError) {
                    _errors.emit(e)
                }
            }
        }
    }

    private fun WriteBatch.add(db: FirebaseFirestore, hid: String, actor: Actor, change: PantryChange) {
        val stamp = mapOf("updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to actor.uid)
        val created = mapOf("createdAt" to FieldValue.serverTimestamp(), "createdBy" to actor.uid)
        when (change) {
            is PantryChange.CreateProduct ->
                set(
                    db.document("${products(hid)}/${change.product.id}"),
                    productFields(change.product) + created + stamp,
                )

            is PantryChange.UpdateProduct ->
                set(
                    db.document("${products(hid)}/${change.product.id}"),
                    productFields(change.product) + stamp,
                    SetOptions.merge(),
                )

            is PantryChange.CreateBatch ->
                set(db.document("${stock(hid)}/${change.batch.id}"), batchFields(change.batch) + created + stamp)

            is PantryChange.RestoreBatch -> set(
                db.document("${stock(hid)}/${change.batch.id}"),
                batchFields(change.batch) + stamp + mapOf(
                    "createdAt" to (change.batch.createdAt?.let { Timestamp(it) } ?: FieldValue.serverTimestamp()),
                    "createdBy" to actor.uid,
                ),
            )

            is PantryChange.AddToBatch ->
                update(
                    db.document("${stock(hid)}/${change.batchId}"),
                    mapOf("quantity" to FieldValue.increment(change.delta)) + stamp,
                )

            is PantryChange.UpdateBatch -> update(
                db.document("${stock(hid)}/${change.batchId}"),
                mapOf(
                    "quantity" to change.edit.quantity,
                    "expiryDate" to change.edit.expiryDate?.toString(),
                    "dateType" to change.edit.dateType.wire,
                    "location" to change.edit.location.wire,
                    "note" to change.edit.note,
                ) + stamp,
            )

            is PantryChange.SyncBatchProduct -> update(
                db.document("${stock(hid)}/${change.batchId}"),
                mapOf("productName" to change.name, "unit" to change.unit.wire, "category" to change.category.wire) +
                    stamp,
            )

            is PantryChange.DeleteBatch -> delete(db.document("${stock(hid)}/${change.batchId}"))

            is PantryChange.LogEvent -> set(
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

            is PantryChange.DeleteEvent -> delete(db.document("${activity(hid)}/${change.eventId}"))
        }
    }

    private companion object {
        const val TYPE_ADDED = "stock_added"
        const val TYPE_DISCARDED = "discarded"

        fun products(hid: String) = "households/$hid/products"

        fun stock(hid: String) = "households/$hid/stock"

        fun activity(hid: String) = "households/$hid/activity"

        /** Every field written explicitly, nulls included (DESIGN 8.1). */
        fun productFields(p: Product) = mapOf(
            "name" to p.name,
            "nameKey" to p.nameKey,
            "category" to p.category.wire,
            "defaultLocation" to p.defaultLocation.wire,
            "unit" to p.unit.wire,
            "minQuantity" to p.minQuantity,
            "autoAddToGrocery" to p.autoAddToGrocery,
            "buyQuantity" to p.buyQuantity,
            "restockArmed" to p.restockArmed,
            "typicalShelfLifeDays" to p.typicalShelfLifeDays,
            "recentShelfLifeDays" to p.recentShelfLifeDays,
        )

        fun batchFields(b: StockBatch) = mapOf(
            "productId" to b.productId,
            "productName" to b.productName,
            "unit" to b.unit.wire,
            "category" to b.category.wire,
            "quantity" to b.quantity,
            "expiryDate" to b.expiryDate?.toString(),
            "dateType" to b.dateType.wire,
            "location" to b.location.wire,
            "note" to b.note,
        )

        // New fields are optional on read with defaults, because phones run different versions (DESIGN 8.1).
        fun DocumentSnapshot.toProduct() = Product(
            id = id,
            name = getString("name").orEmpty(),
            nameKey = getString("nameKey").orEmpty(),
            category = Category.fromWire(getString("category")),
            defaultLocation = Location.fromWire(getString("defaultLocation")),
            unit = QuantityUnit.fromWire(getString("unit")),
            minQuantity = getDouble("minQuantity"),
            autoAddToGrocery = getBoolean("autoAddToGrocery") == true,
            buyQuantity = getDouble("buyQuantity"),
            restockArmed = getBoolean("restockArmed") ?: true,
            typicalShelfLifeDays = getLong("typicalShelfLifeDays")?.toInt(),
            recentShelfLifeDays = (get("recentShelfLifeDays") as? List<*>)?.mapNotNull {
                (it as? Number)?.toInt()
            }.orEmpty(),
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toInstant(),
        )

        fun DocumentSnapshot.toBatch() = StockBatch(
            id = id,
            productId = getString("productId").orEmpty(),
            productName = getString("productName").orEmpty(),
            unit = QuantityUnit.fromWire(getString("unit")),
            category = Category.fromWire(getString("category")),
            quantity = getDouble("quantity") ?: 0.0,
            expiryDate = getString("expiryDate")?.let { parseDate(it) },
            dateType = DateType.fromWire(getString("dateType")),
            location = Location.fromWire(getString("location")),
            note = getString("note"),
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toInstant(),
            pendingSync = metadata.hasPendingWrites(),
        )

        fun DocumentSnapshot.toEvent(): PantryEvent? {
            val type = when (getString("type")) {
                TYPE_ADDED -> PantryEvent.Type.Added
                TYPE_DISCARDED -> PantryEvent.Type.Discarded
                else -> return null
            }

            @Suppress("UNCHECKED_CAST")
            val params = get("params") as? Map<String, Any?>
            return PantryEvent(
                id = id,
                type = type,
                actorName = getString("actorName").orEmpty(),
                quantity = (params?.get("quantity") as? Number)?.toDouble(),
                reason = DiscardReason.fromWire(params?.get("reason") as? String),
                createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toInstant(),
            )
        }

        fun parseDate(value: String): LocalDate? = try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
