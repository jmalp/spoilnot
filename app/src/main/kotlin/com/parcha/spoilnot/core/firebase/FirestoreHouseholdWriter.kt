package com.parcha.spoilnot.core.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.parcha.spoilnot.core.coroutines.ApplicationScope
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.core.write.HouseholdWriter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton
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
}

@Singleton
class FirestoreHouseholdWriter @Inject constructor(
    private val firestore: FirestoreProvider,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val mappers: Set<@JvmSuppressWildcards FirestoreChangeMapper>,
) : HouseholdWriter {
    private val _errors = MutableSharedFlow<AppError>(extraBufferCapacity = 8)
    override val errors: SharedFlow<AppError> = _errors

    override fun newId(): String = firestore.current.collection("ids").document().id

    override suspend fun apply(householdId: String, actor: Actor, changes: List<HouseholdChange>) {
        val db = firestore.current
        changes.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { change ->
                check(mappers.any { it.write(batch, db, householdId, actor, change) }) { "No mapper for $change" }
            }
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

    private companion object {
        /** DESIGN 8.5: chunk batched writes at 400 operations. */
        const val BATCH_LIMIT = 400
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
