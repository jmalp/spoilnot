// Households, membership and joining (feature/household/data/FirestoreHouseholdRepository in the app), and Clear
// household data.
import {
  collection,
  doc,
  getDocFromServer,
  getDocsFromServer,
  serverTimestamp,
  writeBatch,
  type DocumentReference,
  type DocumentSnapshot,
  type Firestore,
  type WriteBatch,
} from 'firebase/firestore';
import { catchError, distinctUntilChanged, filter, map, of, switchMap, throwError, type Observable } from 'rxjs';
import { isAppError } from '../core/errors';
import type { PendingJoin } from '../domain/account';
import {
  HouseholdRules,
  type Household,
  type HouseholdRepository,
  type InviteCode,
  type JoinRequest,
  type LocalData,
  type Member,
  maskEmail,
  type Membership,
  type ResetOutcome,
  type Role,
} from '../domain/household';
import type { Actor } from '../domain/write';
import { Tombstones, type SyncLog } from './delta-sync';
import type { FirebaseSdk } from './firebase';
import {
  commitConfirmed,
  commitQueued,
  docSnapshots,
  mapped,
  querySnapshots,
  retryAfterOwnWrites,
} from './firestore-rx';
import { UserFields } from './users';
import { capTo, ReadCaps } from '../domain/caps';
import { mapEach } from './fields';

const SCHEMA_VERSION = 1;
/** DESIGN 8.5: chunk batched writes at 400 operations. */
const BATCH_LIMIT = 400;
/** Members go last so the owner keeps access while the rest is deleted. */
const FLAT_SUBCOLLECTIONS = ['products', 'stock', 'recipes', 'meals', 'activity', 'stats', 'joinRequests', 'members'];

const householdPath = (hid: string) => `households/${hid}`;
const memberPath = (hid: string, uid: string) => `households/${hid}/members/${uid}`;
const requestPath = (hid: string, uid: string) => `households/${hid}/joinRequests/${uid}`;
const codePath = (code: string) => `inviteCodes/${code}`;

const roleOf = (s: DocumentSnapshot): Role => (s.get('role') === 'owner' ? 'owner' : 'member');
const dateOf = (value: unknown): Date | null =>
  value && typeof (value as { toDate?: unknown }).toDate === 'function'
    ? (value as { toDate: () => Date }).toDate()
    : null;
const text = (s: DocumentSnapshot, field: string): string => {
  const value: unknown = s.get(field);
  return typeof value === 'string' ? value : '';
};
const same = <T>(a: T, b: T) => JSON.stringify(a) === JSON.stringify(b);

export function toHousehold(s: DocumentSnapshot): Household {
  return {
    id: s.id,
    name: capTo(text(s, 'name'), ReadCaps.LIST_NAME),
    ownerUid: text(s, 'ownerUid'),
    inviteCode: text(s, 'inviteCode'),
  };
}

export function toMember(d: DocumentSnapshot): Member {
  return {
    uid: d.id,
    displayName: capTo(text(d, 'displayName'), ReadCaps.PERSON),
    role: roleOf(d),
    joinedAt: dateOf(d.get('joinedAt')),
  };
}

export function toInviteCode(s: DocumentSnapshot): InviteCode {
  return {
    code: s.id,
    householdId: text(s, 'householdId'),
    householdName: capTo(text(s, 'householdName'), ReadCaps.LIST_NAME),
    active: s.get('active') === true,
  };
}

export function toJoinRequest(s: DocumentSnapshot): JoinRequest | null {
  const status: unknown = s.get('status');
  if (status !== 'pending' && status !== 'approved' && status !== 'declined') return null;
  return {
    uid: s.id,
    displayName: capTo(text(s, 'displayName'), ReadCaps.PERSON),
    code: text(s, 'joinCode'),
    status,
    requestedAt: dateOf(s.get('requestedAt')),
    // Older builds don't write it; another type is ignored rather than failing the list.
    emailMasked: typeof s.get('emailMasked') === 'string' ? capTo(text(s, 'emailMasked'), ReadCaps.PERSON * 3) : null,
  };
}

const profilePointer = (householdId: string | null) => ({
  [UserFields.HOUSEHOLD_ID]: householdId,
  [UserFields.PENDING_JOIN]: null,
  [UserFields.UPDATED_AT]: serverTimestamp(),
});

const codeDoc = (householdId: string, householdName: string, uid: string, active: boolean) => ({
  householdId,
  householdName,
  active,
  createdBy: uid,
  createdAt: serverTimestamp(),
});

export class FirestoreHouseholdRepository implements HouseholdRepository {
  constructor(private readonly sdk: FirebaseSdk) {}

  private follow<T>(listen: (db: Firestore) => Observable<T>): Observable<T> {
    return this.sdk.instance$.pipe(switchMap(listen));
  }

  observeMembership(householdId: string, uid: string): Observable<Membership> {
    return this.follow((db) =>
      docSnapshots(doc(db, memberPath(householdId, uid)), true).pipe(retryAfterOwnWrites(db)),
    ).pipe(
      filter((s) => s.exists() || !s.metadata.fromCache),
      map((s): Membership => (s.exists() ? { kind: 'active', role: roleOf(s) } : { kind: 'gone' })),
      // Once removed, the rules deny reading even our own member document (DESIGN 12.4). A refusal that survives
      // retryAfterOwnWrites is a real removal.
      catchError((error: unknown) =>
        isAppError(error, 'permissionDenied') ? of<Membership>({ kind: 'gone' }) : throwError(() => error),
      ),
      distinctUntilChanged(same),
    );
  }

  observeHousehold(householdId: string): Observable<Household | null> {
    return this.follow((db) => docSnapshots(doc(db, householdPath(householdId))).pipe(retryAfterOwnWrites(db))).pipe(
      map((s) => (s.exists() ? toHousehold(s) : null)),
      distinctUntilChanged(same),
    );
  }

  observeMembers(householdId: string): Observable<Member[]> {
    return this.follow((db) =>
      querySnapshots(collection(db, `${householdPath(householdId)}/members`)).pipe(retryAfterOwnWrites(db)),
    ).pipe(
      map((snapshot) =>
        mapEach(snapshot.docs, toMember)
          // Ordered by join date; a join not yet stamped by the server goes last.
          .sort((a, b) => (a.joinedAt?.getTime() ?? Infinity) - (b.joinedAt?.getTime() ?? Infinity)),
      ),
      distinctUntilChanged(same),
    );
  }

  observeJoinRequests(householdId: string): Observable<JoinRequest[]> {
    return this.follow((db) =>
      querySnapshots(collection(db, `${householdPath(householdId)}/joinRequests`)).pipe(retryAfterOwnWrites(db)),
    ).pipe(
      map((snapshot) =>
        mapEach(snapshot.docs, toJoinRequest).sort(
          (a, b) => (a.requestedAt?.getTime() ?? Infinity) - (b.requestedAt?.getTime() ?? Infinity),
        ),
      ),
      distinctUntilChanged(same),
    );
  }

  observeOwnJoinRequest(householdId: string, uid: string): Observable<JoinRequest | null> {
    return this.follow((db) => docSnapshots(doc(db, requestPath(householdId, uid)), true)).pipe(
      filter((s) => s.exists() || !s.metadata.fromCache),
      map((s) => (s.exists() ? toJoinRequest(s) : null)),
      distinctUntilChanged(same),
    );
  }

  observeInviteCode(code: string): Observable<InviteCode | null> {
    return this.follow((db) => docSnapshots(doc(db, codePath(code))).pipe(retryAfterOwnWrites(db))).pipe(
      map((s) => (s.exists() ? toInviteCode(s) : null)),
      distinctUntilChanged(same),
    );
  }

  async createHousehold(uid: string, displayName: string, name: string, inviteCode: string): Promise<string> {
    const db = this.sdk.current;
    const household = doc(collection(db, 'households'));
    const defaultList = doc(collection(household, 'groceryLists'));
    const batch = writeBatch(db);
    batch.set(household, {
      name: name.trim(),
      ownerUid: uid,
      inviteCode,
      schemaVersion: SCHEMA_VERSION,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    batch.set(doc(db, memberPath(household.id, uid)), { role: 'owner', displayName, joinedAt: serverTimestamp() });
    batch.set(doc(db, codePath(inviteCode)), codeDoc(household.id, name.trim(), uid, true));
    batch.set(defaultList, {
      name: HouseholdRules.DEFAULT_GROCERY_LIST_NAME,
      isDefault: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    batch.update(doc(db, UserFields.path(uid)), profilePointer(household.id));
    await commitQueued(batch);
    return household.id;
  }

  async findInviteCode(code: string): Promise<InviteCode | null> {
    const snapshot = await mapped(getDocFromServer(doc(this.sdk.current, codePath(code))));
    return snapshot.exists() ? toInviteCode(snapshot) : null;
  }

  async requestToJoin(uid: string, displayName: string, invite: InviteCode, email?: string | null): Promise<void> {
    const db = this.sdk.current;
    const pending: PendingJoin = {
      householdId: invite.householdId,
      householdName: invite.householdName,
      code: invite.code,
    };
    // After v1: the owner sees who is asking, not only the name they typed. The rules check it.
    const masked = maskEmail(email);
    const send = async (emailMasked: string | null) => {
      // Must reach the server: the rules check the code there, and a rejection must be shown now.
      const batch = writeBatch(db);
      batch.set(doc(db, requestPath(invite.householdId, uid)), {
        displayName,
        joinCode: invite.code,
        status: 'pending',
        requestedAt: serverTimestamp(),
        ...(emailMasked === null ? {} : { emailMasked }),
      });
      batch.update(doc(db, UserFields.path(uid)), {
        [UserFields.PENDING_JOIN]: UserFields.pendingJoin(pending),
        [UserFields.UPDATED_AT]: serverTimestamp(),
      });
      await commitConfirmed(batch);
    };
    try {
      await send(masked);
    } catch (error) {
      // Rules from before the field refuse it: send the request without it (the same refusal for any other reason,
      // such as a paused code, comes back again).
      if (masked === null || !isAppError(error, 'permissionDenied')) throw error;
      await send(null);
    }
  }

  async completeJoin(uid: string, displayName: string, pending: PendingJoin): Promise<void> {
    const db = this.sdk.current;
    const hid = pending.householdId;
    const batch = writeBatch(db);
    batch.update(doc(db, UserFields.path(uid)), profilePointer(hid));
    batch.delete(doc(db, requestPath(hid, uid)));
    // member_joined (DESIGN 5.6), written by the new member: the owner's approval already made them a member.
    batch.set(doc(collection(db, `households/${hid}/activity`)), {
      type: 'member_joined',
      actorUid: uid,
      actorName: displayName,
      params: {},
      createdAt: serverTimestamp(),
    });
    await commitQueued(batch);
  }

  async abandonJoin(uid: string, pending: PendingJoin): Promise<void> {
    const db = this.sdk.current;
    const batch = writeBatch(db);
    batch.delete(doc(db, requestPath(pending.householdId, uid)));
    batch.update(doc(db, UserFields.path(uid)), {
      [UserFields.PENDING_JOIN]: null,
      [UserFields.UPDATED_AT]: serverTimestamp(),
    });
    await commitQueued(batch);
  }

  async approve(householdId: string, request: JoinRequest): Promise<void> {
    const db = this.sdk.current;
    const batch = writeBatch(db);
    batch.update(doc(db, requestPath(householdId, request.uid)), { status: 'approved' });
    batch.set(doc(db, memberPath(householdId, request.uid)), {
      role: 'member',
      displayName: request.displayName,
      joinedAt: serverTimestamp(),
    });
    await commitQueued(batch);
  }

  async decline(householdId: string, requesterUid: string): Promise<void> {
    const db = this.sdk.current;
    const batch = writeBatch(db);
    batch.update(doc(db, requestPath(householdId, requesterUid)), { status: 'declined' });
    await commitQueued(batch);
  }

  async rename(household: Household, newName: string): Promise<void> {
    const db = this.sdk.current;
    const batch = writeBatch(db);
    batch.update(doc(db, householdPath(household.id)), { name: newName.trim(), updatedAt: serverTimestamp() });
    batch.update(doc(db, codePath(household.inviteCode)), { householdName: newName.trim() });
    await commitQueued(batch);
  }

  async regenerateInviteCode(household: Household, uid: string, newCode: string, active: boolean): Promise<void> {
    const db = this.sdk.current;
    const batch = writeBatch(db);
    batch.set(doc(db, codePath(newCode)), codeDoc(household.id, household.name, uid, active));
    batch.update(doc(db, householdPath(household.id)), { inviteCode: newCode, updatedAt: serverTimestamp() });
    batch.delete(doc(db, codePath(household.inviteCode)));
    await commitQueued(batch);
  }

  async setJoiningPaused(code: string, paused: boolean): Promise<void> {
    const db = this.sdk.current;
    const batch = writeBatch(db);
    batch.update(doc(db, codePath(code)), { active: !paused });
    await commitQueued(batch);
  }

  async removeMember(householdId: string, memberUid: string): Promise<void> {
    const db = this.sdk.current;
    const batch = writeBatch(db);
    batch.delete(doc(db, memberPath(householdId, memberUid)));
    await commitQueued(batch);
  }

  async transferOwnership(householdId: string, fromUid: string, toUid: string): Promise<void> {
    const db = this.sdk.current;
    const batch = writeBatch(db);
    batch.update(doc(db, householdPath(householdId)), { ownerUid: toUid, updatedAt: serverTimestamp() });
    batch.update(doc(db, memberPath(householdId, toUid)), { role: 'owner' });
    batch.update(doc(db, memberPath(householdId, fromUid)), { role: 'member' });
    await commitConfirmed(batch);
  }

  async leave(householdId: string, uid: string): Promise<void> {
    const db = this.sdk.current;
    const batch = writeBatch(db);
    batch.delete(doc(db, memberPath(householdId, uid)));
    batch.update(doc(db, UserFields.path(uid)), profilePointer(null));
    await commitConfirmed(batch);
  }

  async deleteHousehold(household: Household, uid: string): Promise<void> {
    const db = this.sdk.current;
    const refs = (await this.subcollectionDocuments(db, household.id)).filter(
      (ref) => ref.path !== memberPath(household.id, uid),
    );
    for (let i = 0; i < refs.length; i += BATCH_LIMIT) {
      const batch = writeBatch(db);
      refs.slice(i, i + BATCH_LIMIT).forEach((ref) => batch.delete(ref));
      await commitConfirmed(batch);
    }
    const batch = writeBatch(db);
    batch.delete(doc(db, codePath(household.inviteCode)));
    batch.delete(doc(db, memberPath(household.id, uid)));
    batch.delete(doc(db, householdPath(household.id)));
    batch.update(doc(db, UserFields.path(uid)), profilePointer(null));
    await commitConfirmed(batch);
  }

  async resetData(householdId: string, actor: Actor): Promise<ResetOutcome> {
    const db = this.sdk.current;
    const base = householdPath(householdId);
    const read = async (path: string) => (await mapped(getDocsFromServer(collection(db, path)))).docs;
    const live = async (path: string) => (await read(path)).filter((d) => !Tombstones.is(d));
    const ops: ((batch: WriteBatch) => void)[] = [];
    const tombstone = Tombstones.fields(actor);
    // Tombstones, not deletes: other phones read only what changed (DESIGN 9.8) and must see these go.
    for (const name of ['products', 'stock', 'recipes', 'meals']) {
      for (const d of await live(`${base}/${name}`)) ops.push((b) => b.set(d.ref, tombstone, { merge: true }));
    }
    for (const list of await read(`${base}/groceryLists`)) {
      const items = (await mapped(getDocsFromServer(collection(list.ref, 'items')))).docs;
      if (list.get('isDefault') === true) {
        items
          .filter((d) => !Tombstones.is(d))
          .forEach((d) => ops.push((b) => b.set(d.ref, tombstone, { merge: true })));
        ops.push((b) =>
          b.update(list.ref, {
            name: HouseholdRules.DEFAULT_GROCERY_LIST_NAME,
            aisleOrder: [],
            updatedAt: serverTimestamp(),
          }),
        );
      } else {
        // Nothing listens to a deleted list's items, so they go for real.
        items.forEach((d) => ops.push((b) => b.delete(d.ref)));
        ops.push((b) => b.delete(list.ref));
      }
    }
    for (const name of ['activity', 'stats']) {
      (await read(`${base}/${name}`)).forEach((d) => ops.push((b) => b.delete(d.ref)));
    }
    // Last, so Recent activity everywhere starts from here (older events cached on a phone are hidden).
    ops.push((b) =>
      b.set(doc(collection(db, `${base}/activity`)), {
        type: 'household_reset',
        actorUid: actor.uid,
        actorName: actor.name,
        params: {},
        createdAt: serverTimestamp(),
      }),
    );
    // A chunk that times out is still queued: queue the rest after it rather than stop half done (SYNC-08).
    let queued = false;
    for (let i = 0; i < ops.length; i += BATCH_LIMIT) {
      const batch = writeBatch(db);
      ops.slice(i, i + BATCH_LIMIT).forEach((op) => op(batch));
      if (queued) {
        batch.commit().catch(() => undefined);
        continue;
      }
      try {
        await commitConfirmed(batch);
      } catch (error) {
        if (!isAppError(error, 'offline')) throw error;
        queued = true;
      }
    }
    return queued ? 'finishesWhenOnline' : 'cleared';
  }

  /** Every document under the household, deepest first, read from the server so nothing is missed. */
  private async subcollectionDocuments(db: Firestore, householdId: string): Promise<DocumentReference[]> {
    const base = householdPath(householdId);
    const result: DocumentReference[] = [];
    const lists = await mapped(getDocsFromServer(collection(db, `${base}/groceryLists`)));
    for (const list of lists.docs) {
      const items = await mapped(getDocsFromServer(collection(list.ref, 'items')));
      result.push(...items.docs.map((d) => d.ref));
    }
    result.push(...lists.docs.map((d) => d.ref));
    for (const name of FLAT_SUBCOLLECTIONS) {
      const docs = await mapped(getDocsFromServer(collection(db, `${base}/${name}`)));
      result.push(...docs.docs.map((d) => d.ref));
    }
    return result;
  }
}

/** The household's copy in this browser: Firestore's cache, and the sync log that names its collections. */
export class FirestoreLocalData implements LocalData {
  constructor(
    private readonly sdk: FirebaseSdk,
    private readonly log: SyncLog,
  ) {}
  async clear(): Promise<void> {
    try {
      await this.sdk.clearLocalData();
    } finally {
      this.log.clear();
    }
  }

  flushPendingWrites(): Promise<boolean> {
    return this.sdk.flushPendingWrites();
  }
}
