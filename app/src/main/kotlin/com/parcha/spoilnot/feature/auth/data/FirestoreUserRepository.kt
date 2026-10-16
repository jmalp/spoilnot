package com.parcha.spoilnot.feature.auth.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.core.firebase.awaitMapped
import com.parcha.spoilnot.core.firebase.commitConfirmed
import com.parcha.spoilnot.core.firebase.commitQueued
import com.parcha.spoilnot.core.firebase.snapshotFlow
import com.parcha.spoilnot.feature.auth.domain.PendingJoin
import com.parcha.spoilnot.feature.auth.domain.ProfileDefaults
import com.parcha.spoilnot.feature.auth.domain.UserProfile
import com.parcha.spoilnot.feature.auth.domain.UserRepository
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.transform

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreUserRepository @Inject constructor(private val firestore: FirestoreProvider) : UserRepository {
    override fun observeProfile(uid: String): Flow<UserProfile?> = firestore.instance
        .flatMapLatest { db -> db.document(UserFields.path(uid)).snapshotFlow(MetadataChanges.INCLUDE) }
        .transform { snapshot ->
            when {
                snapshot.exists() -> emit(snapshot.toProfile())

                // A cache miss is not proof of absence; wait for the server.
                !snapshot.metadata.isFromCache -> emit(null)
            }
        }
        .distinctUntilChanged()

    override suspend fun createProfileIfMissing(uid: String, displayName: String) {
        val db = firestore.current
        val ref = db.document(UserFields.path(uid))
        db.runTransaction { tx ->
            if (!tx.get(ref).exists()) {
                tx.set(
                    ref,
                    mapOf(
                        UserFields.DISPLAY_NAME to displayName,
                        UserFields.HOUSEHOLD_ID to null,
                        UserFields.PENDING_JOIN to null,
                        "timezone" to ZoneId.systemDefault().id,
                        "locale" to ProfileDefaults.LOCALE,
                        "units" to ProfileDefaults.UNITS.name.lowercase(),
                        "notificationPrefs" to mapOf(
                            "enabled" to true,
                            "digestMinutes" to ProfileDefaults.DIGEST_MINUTES,
                            "stages" to ProfileDefaults.STAGES,
                            "expiredMode" to ProfileDefaults.EXPIRED_MODE,
                        ),
                        "createdAt" to FieldValue.serverTimestamp(),
                        UserFields.UPDATED_AT to FieldValue.serverTimestamp(),
                    ),
                )
            }
        }.awaitMapped()
    }

    override suspend fun clearHousehold(uid: String) {
        val db = firestore.current
        db.batch()
            .update(
                db.document(UserFields.path(uid)),
                mapOf(UserFields.HOUSEHOLD_ID to null, UserFields.UPDATED_AT to FieldValue.serverTimestamp()),
            )
            .commitQueued()
    }

    override suspend fun deleteProfile(uid: String) {
        val db = firestore.current
        val devices = db.collection("${UserFields.path(uid)}/devices").get(Source.SERVER).awaitMapped()
        val batch = db.batch()
        devices.documents.forEach { batch.delete(it.reference) }
        batch.delete(db.document(UserFields.path(uid)))
        batch.commitConfirmed()
    }
}

/** Field names of users/{uid}, shared with the household repository, which also writes the profile. */
internal object UserFields {
    const val DISPLAY_NAME = "displayName"
    const val HOUSEHOLD_ID = "householdId"
    const val PENDING_JOIN = "pendingJoin"
    const val UPDATED_AT = "updatedAt"

    fun path(uid: String) = "users/$uid"

    fun pendingJoin(pending: PendingJoin) = mapOf(
        "householdId" to pending.householdId,
        "householdName" to pending.householdName,
        "code" to pending.code,
    )
}

private fun DocumentSnapshot.toProfile(): UserProfile {
    @Suppress("UNCHECKED_CAST")
    val pending = get(UserFields.PENDING_JOIN) as? Map<String, Any?>
    return UserProfile(
        uid = id,
        displayName = getString(UserFields.DISPLAY_NAME).orEmpty(),
        householdId = getString(UserFields.HOUSEHOLD_ID),
        pendingJoin = pending?.let {
            val householdId = it["householdId"] as? String
            val code = it["code"] as? String
            if (householdId == null || code == null) {
                null
            } else {
                PendingJoin(householdId, it["householdName"] as? String ?: "", code)
            }
        },
    )
}
