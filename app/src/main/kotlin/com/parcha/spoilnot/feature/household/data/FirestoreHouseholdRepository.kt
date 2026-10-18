package com.parcha.spoilnot.feature.household.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.core.firebase.awaitMapped
import com.parcha.spoilnot.core.firebase.commitConfirmed
import com.parcha.spoilnot.core.firebase.commitQueued
import com.parcha.spoilnot.core.firebase.retryAfterOwnWrites
import com.parcha.spoilnot.core.firebase.snapshotFlow
import com.parcha.spoilnot.feature.auth.data.UserFields
import com.parcha.spoilnot.feature.auth.domain.PendingJoin
import com.parcha.spoilnot.feature.household.domain.Household
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.HouseholdRules
import com.parcha.spoilnot.feature.household.domain.InviteCode
import com.parcha.spoilnot.feature.household.domain.JoinRequest
import com.parcha.spoilnot.feature.household.domain.JoinRequestStatus
import com.parcha.spoilnot.feature.household.domain.LocalData
import com.parcha.spoilnot.feature.household.domain.Member
import com.parcha.spoilnot.feature.household.domain.Membership
import com.parcha.spoilnot.feature.household.domain.Role
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreHouseholdRepository @Inject constructor(private val firestore: FirestoreProvider) :
    HouseholdRepository {

    override fun observeMembership(householdId: String, uid: String): Flow<Membership> = firestore.instance
        .flatMapLatest { db ->
            db.document(memberPath(householdId, uid)).snapshotFlow(MetadataChanges.INCLUDE).retryAfterOwnWrites(db)
        }
        .transform { snapshot ->
            when {
                snapshot.exists() -> emit(Membership.Active(snapshot.role()))
                !snapshot.metadata.isFromCache -> emit(Membership.Gone)
            }
        }
        // Once removed, the rules deny reading even our own member document (DESIGN 12.4). A refusal that
        // survives retryAfterOwnWrites is a real removal.
        .catch { e -> if (e is AppError.PermissionDenied) emit(Membership.Gone) else throw e }
        .distinctUntilChanged()

    override fun observeHousehold(householdId: String): Flow<Household?> = firestore.instance
        .flatMapLatest { db -> db.document(householdPath(householdId)).snapshotFlow().retryAfterOwnWrites(db) }
        .map { if (it.exists()) it.toHousehold() else null }
        .distinctUntilChanged()

    override fun observeMembers(householdId: String): Flow<List<Member>> = firestore.instance
        .flatMapLatest { db ->
            db.collection("${householdPath(householdId)}/members").snapshotFlow().retryAfterOwnWrites(db)
        }
        .map { snapshot ->
            snapshot.documents
                .map {
                    Member(
                        it.id,
                        it.getString("displayName").orEmpty(),
                        it.role(),
                        it.getTimestamp("joinedAt")?.toInstant(),
                    )
                }
                .sortedWith(compareBy(nullsLast()) { it.joinedAt })
        }
        .distinctUntilChanged()

    override fun observeJoinRequests(householdId: String): Flow<List<JoinRequest>> = firestore.instance
        .flatMapLatest { db ->
            db.collection("${householdPath(householdId)}/joinRequests").snapshotFlow().retryAfterOwnWrites(db)
        }
        .map { snapshot -> snapshot.documents.mapNotNull { it.toJoinRequest() }.sortedBy { it.requestedAt } }
        .distinctUntilChanged()

    override fun observeOwnJoinRequest(householdId: String, uid: String): Flow<JoinRequest?> = firestore.instance
        .flatMapLatest { db -> db.document(requestPath(householdId, uid)).snapshotFlow(MetadataChanges.INCLUDE) }
        .transform { snapshot ->
            when {
                snapshot.exists() -> emit(snapshot.toJoinRequest())
                !snapshot.metadata.isFromCache -> emit(null)
            }
        }
        .distinctUntilChanged()

    override fun observeInviteCode(code: String): Flow<InviteCode?> = firestore.instance
        .flatMapLatest { db -> db.document(codePath(code)).snapshotFlow().retryAfterOwnWrites(db) }
        .map { if (it.exists()) it.toInviteCode() else null }
        .distinctUntilChanged()

    override suspend fun createHousehold(uid: String, displayName: String, name: String, inviteCode: String): String {
        val db = firestore.current
        val household = db.collection("households").document()
        val defaultList = household.collection("groceryLists").document()
        db.batch()
            .set(
                household,
                mapOf(
                    "name" to name.trim(),
                    "ownerUid" to uid,
                    "inviteCode" to inviteCode,
                    "schemaVersion" to SCHEMA_VERSION,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .set(
                db.document(memberPath(household.id, uid)),
                mapOf("role" to ROLE_OWNER, "displayName" to displayName, "joinedAt" to FieldValue.serverTimestamp()),
            )
            .set(db.document(codePath(inviteCode)), codeDoc(household.id, name.trim(), uid, active = true))
            .set(
                defaultList,
                mapOf(
                    "name" to HouseholdRules.DEFAULT_GROCERY_LIST_NAME,
                    "isDefault" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .update(db.document(UserFields.path(uid)), profilePointer(household.id))
            .commitQueued()
        return household.id
    }

    override suspend fun findInviteCode(code: String): InviteCode? {
        val snapshot = firestore.current.document(codePath(code)).get(Source.SERVER).awaitMapped()
        return if (snapshot.exists()) snapshot.toInviteCode() else null
    }

    override suspend fun requestToJoin(uid: String, displayName: String, invite: InviteCode) {
        val db = firestore.current
        val pending = PendingJoin(invite.householdId, invite.householdName, invite.code)
        // Must reach the server: the rules check the code there, and a rejection must be shown now.
        db.batch()
            .set(
                db.document(requestPath(invite.householdId, uid)),
                mapOf(
                    "displayName" to displayName,
                    "joinCode" to invite.code,
                    "status" to STATUS_PENDING,
                    "requestedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .update(
                db.document(UserFields.path(uid)),
                mapOf(
                    UserFields.PENDING_JOIN to UserFields.pendingJoin(pending),
                    UserFields.UPDATED_AT to FieldValue.serverTimestamp(),
                ),
            )
            .commitConfirmed()
    }

    override suspend fun completeJoin(uid: String, displayName: String, pending: PendingJoin) {
        val db = firestore.current
        val hid = pending.householdId
        db.batch()
            .update(db.document(UserFields.path(uid)), profilePointer(hid))
            .delete(db.document(requestPath(hid, uid)))
            // member_joined (DESIGN 5.6), written by the new member: the owner's approval already made them a member.
            .set(
                db.collection("households/$hid/activity").document(),
                mapOf(
                    "type" to "member_joined",
                    "actorUid" to uid,
                    "actorName" to displayName,
                    "params" to emptyMap<String, Any>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            .commitQueued()
    }

    override suspend fun abandonJoin(uid: String, pending: PendingJoin) {
        val db = firestore.current
        db.batch()
            .delete(db.document(requestPath(pending.householdId, uid)))
            .update(
                db.document(UserFields.path(uid)),
                mapOf(UserFields.PENDING_JOIN to null, UserFields.UPDATED_AT to FieldValue.serverTimestamp()),
            )
            .commitQueued()
    }

    override suspend fun approve(householdId: String, request: JoinRequest) {
        val db = firestore.current
        db.batch()
            .update(db.document(requestPath(householdId, request.uid)), "status", STATUS_APPROVED)
            .set(
                db.document(memberPath(householdId, request.uid)),
                mapOf(
                    "role" to ROLE_MEMBER,
                    "displayName" to request.displayName,
                    "joinedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .commitQueued()
    }

    override suspend fun decline(householdId: String, requesterUid: String) {
        val db = firestore.current
        db.batch().update(db.document(requestPath(householdId, requesterUid)), "status", STATUS_DECLINED).commitQueued()
    }

    override suspend fun rename(household: Household, newName: String) {
        val db = firestore.current
        db.batch()
            .update(
                db.document(householdPath(household.id)),
                mapOf("name" to newName.trim(), "updatedAt" to FieldValue.serverTimestamp()),
            )
            .update(db.document(codePath(household.inviteCode)), "householdName", newName.trim())
            .commitQueued()
    }

    override suspend fun regenerateInviteCode(household: Household, uid: String, newCode: String, active: Boolean) {
        val db = firestore.current
        db.batch()
            .set(db.document(codePath(newCode)), codeDoc(household.id, household.name, uid, active))
            .update(
                db.document(householdPath(household.id)),
                mapOf("inviteCode" to newCode, "updatedAt" to FieldValue.serverTimestamp()),
            )
            .delete(db.document(codePath(household.inviteCode)))
            .commitQueued()
    }

    override suspend fun setJoiningPaused(code: String, paused: Boolean) {
        val db = firestore.current
        db.batch().update(db.document(codePath(code)), "active", !paused).commitQueued()
    }

    override suspend fun removeMember(householdId: String, memberUid: String) {
        val db = firestore.current
        db.batch().delete(db.document(memberPath(householdId, memberUid))).commitQueued()
    }

    override suspend fun transferOwnership(householdId: String, fromUid: String, toUid: String) {
        val db = firestore.current
        db.batch()
            .update(
                db.document(householdPath(householdId)),
                mapOf("ownerUid" to toUid, "updatedAt" to FieldValue.serverTimestamp()),
            )
            .update(db.document(memberPath(householdId, toUid)), "role", ROLE_OWNER)
            .update(db.document(memberPath(householdId, fromUid)), "role", ROLE_MEMBER)
            .commitConfirmed()
    }

    override suspend fun leave(householdId: String, uid: String) {
        val db = firestore.current
        db.batch()
            .delete(db.document(memberPath(householdId, uid)))
            .update(db.document(UserFields.path(uid)), profilePointer(null))
            .commitConfirmed()
    }

    override suspend fun deleteHousehold(household: Household, uid: String) {
        val db = firestore.current
        val refs = subcollectionDocuments(db, household.id)
            .filterNot { it.path == memberPath(household.id, uid) }
        refs.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach(batch::delete)
            batch.commitConfirmed()
        }
        db.batch()
            .delete(db.document(codePath(household.inviteCode)))
            .delete(db.document(memberPath(household.id, uid)))
            .delete(db.document(householdPath(household.id)))
            .update(db.document(UserFields.path(uid)), profilePointer(null))
            .commitConfirmed()
    }

    /** Every document under the household, deepest first, read from the server so nothing is missed. */
    private suspend fun subcollectionDocuments(db: FirebaseFirestore, householdId: String): List<DocumentReference> {
        val base = householdPath(householdId)
        val result = mutableListOf<DocumentReference>()
        val lists = db.collection("$base/groceryLists").get(Source.SERVER).awaitMapped().documents
        lists.forEach { list ->
            result += list.reference.collection("items").get(Source.SERVER).awaitMapped().documents.map { it.reference }
        }
        result += lists.map { it.reference }
        for (name in FLAT_SUBCOLLECTIONS) {
            result += db.collection("$base/$name").get(Source.SERVER).awaitMapped().documents.map { it.reference }
        }
        return result
    }

    private fun profilePointer(householdId: String?) = mapOf(
        UserFields.HOUSEHOLD_ID to householdId,
        UserFields.PENDING_JOIN to null,
        UserFields.UPDATED_AT to FieldValue.serverTimestamp(),
    )

    private fun codeDoc(householdId: String, householdName: String, uid: String, active: Boolean) = mapOf(
        "householdId" to householdId,
        "householdName" to householdName,
        "active" to active,
        "createdBy" to uid,
        "createdAt" to FieldValue.serverTimestamp(),
    )

    private companion object {
        const val SCHEMA_VERSION = 1
        const val ROLE_OWNER = "owner"
        const val ROLE_MEMBER = "member"
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_DECLINED = "declined"

        /** DESIGN 8.5: chunk batched writes at 400 operations. */
        const val BATCH_LIMIT = 400

        /** Members go last so the owner keeps access while the rest is deleted. */
        val FLAT_SUBCOLLECTIONS = listOf("products", "stock", "recipes", "activity", "joinRequests", "members")

        fun householdPath(hid: String) = "households/$hid"

        fun memberPath(hid: String, uid: String) = "households/$hid/members/$uid"

        fun requestPath(hid: String, uid: String) = "households/$hid/joinRequests/$uid"

        fun codePath(code: String) = "inviteCodes/$code"

        fun DocumentSnapshot.role() = if (getString("role") == ROLE_OWNER) Role.Owner else Role.Member

        fun DocumentSnapshot.toHousehold() = Household(
            id = id,
            name = getString("name").orEmpty(),
            ownerUid = getString("ownerUid").orEmpty(),
            inviteCode = getString("inviteCode").orEmpty(),
        )

        fun DocumentSnapshot.toInviteCode() = InviteCode(
            code = id,
            householdId = getString("householdId").orEmpty(),
            householdName = getString("householdName").orEmpty(),
            active = getBoolean("active") == true,
        )

        fun DocumentSnapshot.toJoinRequest(): JoinRequest? {
            val status = when (getString("status")) {
                STATUS_PENDING -> JoinRequestStatus.Pending
                STATUS_APPROVED -> JoinRequestStatus.Approved
                STATUS_DECLINED -> JoinRequestStatus.Declined
                else -> return null
            }
            return JoinRequest(
                uid = id,
                displayName = getString("displayName").orEmpty(),
                code = getString("joinCode").orEmpty(),
                status = status,
                requestedAt = getTimestamp("requestedAt")?.toInstant(),
            )
        }
    }
}

class FirestoreLocalData @Inject constructor(private val firestore: FirestoreProvider) : LocalData {
    override suspend fun clear() = firestore.clearLocalData()
}
