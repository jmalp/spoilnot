// Households, membership and joining (feature/household/data/FirestoreHouseholdRepository in the app). Clear
// household data (resetData) comes with Settings in W6.
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
  type Membership,
  type Role,
} from '../domain/household';
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

function toHousehold(s: DocumentSnapshot): Household {
  return { id: s.id, name: text(s, 'name'), ownerUid: text(s, 'ownerUid'), inviteCode: text(s, 'inviteCode') };
}

function toInviteCode(s: DocumentSnapshot): InviteCode {
  return {
    code: s.id,
    householdId: text(s, 'householdId'),
    householdName: text(s, 'householdName'),
    active: s.get('active') === true,
  };
}

function toJoinRequest(s: DocumentSnapshot): JoinRequest | null {
  const status: unknown = s.get('status');
  if (status !== 'pending' && status !== 'approved' && status !== 'declined') return null;
  return {
    uid: s.id,
    displayName: text(s, 'displayName'),
    code: text(s, 'joinCode'),
    status,
    requestedAt: dateOf(s.get('requestedAt')),
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
        snapshot.docs
          .map((d): Member => ({
            uid: d.id,
            displayName: text(d, 'displayName'),
            role: roleOf(d),
            joinedAt: dateOf(d.get('joinedAt')),
          }))
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
        snapshot.docs
          .map(toJoinRequest)
          .filter((r): r is JoinRequest => r !== null)
          .sort((a, b) => (a.requestedAt?.getTime() ?? Infinity) - (b.requestedAt?.getTime() ?? Infinity)),
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

  async requestToJoin(uid: string, displayName: string, invite: InviteCode): Promise<void> {
    const db = this.sdk.current;
    const pending: PendingJoin = {
      householdId: invite.householdId,
      householdName: invite.householdName,
      code: invite.code,
    };
    // Must reach the server: the rules check the code there, and a rejection must be shown now.
    const batch = writeBatch(db);
    batch.set(doc(db, requestPath(invite.householdId, uid)), {
      displayName,
      joinCode: invite.code,
      status: 'pending',
      requestedAt: serverTimestamp(),
    });
    batch.update(doc(db, UserFields.path(uid)), {
      [UserFields.PENDING_JOIN]: UserFields.pendingJoin(pending),
      [UserFields.UPDATED_AT]: serverTimestamp(),
    });
    await commitConfirmed(batch);
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

export class FirestoreLocalData implements LocalData {
  constructor(private readonly sdk: FirebaseSdk) {}
  clear(): Promise<void> {
    return this.sdk.clearLocalData();
  }
}
