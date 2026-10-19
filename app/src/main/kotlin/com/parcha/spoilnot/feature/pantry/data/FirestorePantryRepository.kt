package com.parcha.spoilnot.feature.pantry.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.core.firebase.SyncLog
import com.parcha.spoilnot.core.firebase.liveDocuments
import com.parcha.spoilnot.core.firebase.retryAfterOwnWrites
import com.parcha.spoilnot.core.firebase.snapshotFlow
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryEvent
import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class FirestorePantryRepository @Inject constructor(
    private val firestore: FirestoreProvider,
    private val syncLog: SyncLog,
) : PantryRepository {
    // Changes-only listeners (DESIGN 9.8); each batch keeps its pending-sync flag from the cache.
    override fun observe(householdId: String): Flow<PantryData> = firestore.instance.flatMapLatest { db ->
        val products = db.collection(products(householdId)).liveDocuments(db, syncLog)
        val stock = db.collection(stock(householdId)).liveDocuments(db, syncLog)
        combine(products, stock) { p, s ->
            PantryData(
                products = p.documents.map { it.toProduct() },
                batches = s.documents.map { it.toBatch() },
                fromCache = p.fromCache || s.fromCache,
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

    internal companion object {
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
            "lastAddedQuantity" to p.lastAddedQuantity,
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
            lastAddedQuantity = getDouble("lastAddedQuantity"),
            aliases = (get("aliases") as? List<*>)?.filterIsInstance<String>().orEmpty(),
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
