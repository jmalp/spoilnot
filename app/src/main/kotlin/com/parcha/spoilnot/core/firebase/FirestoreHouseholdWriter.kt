package com.parcha.spoilnot.core.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.parcha.spoilnot.core.coroutines.ApplicationScope
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.core.write.WriteBatches
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/** Turns one feature's [HouseholdChange]s into Firestore writes. Each feature's data layer provides one. */
interface FirestoreChangeMapper {
    /** Adds [change] to [batch] and returns true, or returns false when the change belongs to another feature. */
    fun write(
        batch: WriteBatch,
        db: FirebaseFirestore,
        householdId: String,
        actor: Actor,
        change: HouseholdChange,
    ): Boolean

    /**
     * Why the server refused [chunk], one batch this mapper helped write, when another phone finished the same thing
     * first (DESIGN 8.5): read from the server after the refusal, so it costs reads only then. Null when the batch
     * holds none of this feature's races, or nothing shows that one was lost.
     */
    suspend fun whyRefused(db: FirebaseFirestore, householdId: String, chunk: List<HouseholdChange>): AppError? = null
}

@Singleton
class FirestoreHouseholdWriter @Inject constructor(
    private val firestore: FirestoreProvider,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val mappers: Set<@JvmSuppressWildcards FirestoreChangeMapper>,
) : HouseholdWriter {
    private val _errors = MutableSharedFlow<AppError>(extraBufferCapacity = 8)
    override val errors: SharedFlow<AppError> = _errors
    private val _localPlans = MutableSharedFlow<List<HouseholdChange>>(extraBufferCapacity = 16)
    override val localPlans: SharedFlow<List<HouseholdChange>> = _localPlans

    override fun newId(): String = firestore.current.collection("ids").document().id

    override suspend fun apply(householdId: String, actor: Actor, changes: List<HouseholdChange>) {
        val db = firestore.current
        // Counts go one step per write (DESIGN 8.5), so a plan may need more batches than its size alone.
        WriteBatches.plan(changes).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { change ->
                check(mappers.any { it.write(batch, db, householdId, actor, change) }) { "No mapper for $change" }
            }
            val task = batch.commit()
            scope.launch {
                try {
                    task.awaitMapped()
                } catch (e: AppError) {
                    _errors.emit(if (e is AppError.PermissionDenied) explain(db, householdId, chunk) ?: e else e)
                }
            }
        }
        _localPlans.tryEmit(changes)
    }

    /** The race a refused batch lost, if a feature can tell (DESIGN 8.5); a failed read leaves the plain refusal. */
    private suspend fun explain(db: FirebaseFirestore, householdId: String, chunk: List<HouseholdChange>): AppError? =
        try {
            mappers.firstNotNullOfOrNull { it.whyRefused(db, householdId, chunk) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
}

/** The audit fields every mutable household document carries (DESIGN 8.1). */
object Stamps {
    fun updated(actor: Actor) = mapOf("updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to actor.uid)

    fun created(actor: Actor) = mapOf("createdAt" to FieldValue.serverTimestamp(), "createdBy" to actor.uid)
}

@Module
@InstallIn(SingletonComponent::class)
interface HouseholdWriterModule {
    @Binds
    fun householdWriter(impl: FirestoreHouseholdWriter): HouseholdWriter

    @Multibinds
    fun changeMappers(): Set<FirestoreChangeMapper>
}
