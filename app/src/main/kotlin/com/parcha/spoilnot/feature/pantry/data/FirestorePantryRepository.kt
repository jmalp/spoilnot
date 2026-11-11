package com.parcha.spoilnot.feature.pantry.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.parcha.spoilnot.core.coroutines.ApplicationScope
import com.parcha.spoilnot.core.coroutines.SharedByKey
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.core.firebase.LiveDocuments
import com.parcha.spoilnot.core.firebase.SyncLog
import com.parcha.spoilnot.core.firebase.cachedLiveDocuments
import com.parcha.spoilnot.core.firebase.liveDocuments
import com.parcha.spoilnot.core.firebase.retryAfterOwnWrites
import com.parcha.spoilnot.core.firebase.snapshotFlow
import com.parcha.spoilnot.core.write.ReadCaps
import com.parcha.spoilnot.core.write.capTo
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
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestorePantryRepository(
    private val firestore: FirestoreProvider,
    private val syncLog: SyncLog,
    scope: CoroutineScope,
    /** How long the shared listeners stay after the last collector leaves; two-phone tests use 0 to "close the app". */
    stopTimeoutMs: Long,
) : PantryRepository {
    @Inject constructor(firestore: FirestoreProvider, syncLog: SyncLog, @ApplicationScope scope: CoroutineScope) :
        this(firestore, syncLog, scope, SharedByKey.STOP_TIMEOUT_MS)

    /** One pair of listeners per household and phone, shared by every screen (after v1, DESIGN 9.1). */
    private val shared = SharedByKey<String, PantryData>(scope, stopTimeoutMs)

    override fun observe(householdId: String): Flow<PantryData> = shared.get(householdId) { listen(householdId) }

    // Changes-only listeners (DESIGN 9.8); each batch keeps its pending-sync flag from the cache. The mapping runs
    // once per snapshot in the shared flow's scope, off the main thread (after v1, PERF-02).
    private fun listen(householdId: String): Flow<PantryData> = firestore.instance.flatMapLatest { db ->
        val products = db.collection(products(householdId)).let { it.liveDocuments(db, syncLog).serverRead(it.path) }
        val stock = db.collection(stock(householdId)).let { it.liveDocuments(db, syncLog).serverRead(it.path) }
        combine(products, stock) { (p, pRead), (s, sRead) ->
            PantryData(
                products = p.documents.map { it.toProduct() },
                batches = s.documents.map { it.toBatch() },
                fromCache = p.fromCache || s.fromCache,
                serverRead = pRead && sRead,
            )
        }
    }.distinctUntilChanged()

    /**
     * The local cache only, for the restock engine (after v1, DESIGN 9.6): free, and it holds no server listener, so
     * it costs nothing when the app comes back after a long time. "Read from the server" here means a full read of
     * each collection is on record on this phone.
     */
    override fun observeCached(householdId: String): Flow<PantryData> = firestore.instance.flatMapLatest { db ->
        val products = db.collection(products(householdId)).cachedLiveDocuments()
        val stock = db.collection(stock(householdId)).cachedLiveDocuments()
        val read = combine(syncLog.fullSyncs(products(householdId)), syncLog.fullSyncs(stock(householdId))) { p, s ->
            p != null && s != null
        }
        combine(products, stock, read) { p, s, serverRead ->
            PantryData(
                products = p.map { it.toProduct() },
                batches = s.map { it.toBatch() },
                fromCache = true,
                serverRead = serverRead,
            )
        }
    }.distinctUntilChanged()

    /**
     * Pairs each snapshot with whether this collection has been read from the server: a full read on record (a warm
     * cache, so offline still counts) or a server snapshot since this listener started (DESIGN 9.6).
     */
    private fun Flow<LiveDocuments>.serverRead(path: String): Flow<Pair<LiveDocuments, Boolean>> = flow {
        var read = syncLog.lastFullSync(path) != null
        collect { live ->
            read = read || !live.fromCache
            emit(live to read)
        }
    }

    override suspend fun removedBy(householdId: String, batchId: String): String? = try {
        firestore.current.document("${stock(householdId)}/$batchId").get(Source.CACHE).await().getString("updatedBy")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

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
            "openedShelfLifeDays" to p.openedShelfLifeDays,
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
            "openedOn" to b.openedOn?.toString(),
        )

        // New fields are optional on read with defaults, because phones run different versions (DESIGN 8.1).
        fun DocumentSnapshot.toProduct() = Product(
            id = id,
            name = getString("name").orEmpty().capTo(ReadCaps.NAME),
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
            openedShelfLifeDays = getLong("openedShelfLifeDays")?.toInt(),
            updatedBy = getString("updatedBy"),
        )

        fun DocumentSnapshot.toBatch() = StockBatch(
            id = id,
            productId = getString("productId").orEmpty(),
            productName = getString("productName").orEmpty().capTo(ReadCaps.NAME),
            unit = QuantityUnit.fromWire(getString("unit")),
            category = Category.fromWire(getString("category")),
            quantity = getDouble("quantity") ?: 0.0,
            expiryDate = getString("expiryDate")?.let { parseDate(it) },
            dateType = DateType.fromWire(getString("dateType")),
            location = Location.fromWire(getString("location")),
            note = getString("note"),
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toInstant(),
            pendingSync = metadata.hasPendingWrites(),
            openedOn = getString("openedOn")?.let { parseDate(it) },
            updatedBy = getString("updatedBy"),
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
                actorName = getString("actorName").orEmpty().capTo(ReadCaps.PERSON),
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
