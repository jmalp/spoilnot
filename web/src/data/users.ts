// users/{uid} (feature/auth/data/FirestoreUserRepository in the app).
import {
  collection,
  doc,
  getDocsFromServer,
  runTransaction,
  serverTimestamp,
  writeBatch,
  type DocumentSnapshot,
} from 'firebase/firestore';
import { distinctUntilChanged, filter, map, switchMap, type Observable } from 'rxjs';
import {
  ProfileDefaults,
  type PendingJoin,
  type Units,
  type UserProfile,
  type UserRepository,
} from '../domain/account';
import type { FirebaseSdk } from './firebase';
import { commitConfirmed, commitQueued, docSnapshots, mapped } from './firestore-rx';

/** Field names of users/{uid}, shared with the household repository, which also writes the profile. */
export const UserFields = {
  DISPLAY_NAME: 'displayName',
  HOUSEHOLD_ID: 'householdId',
  PENDING_JOIN: 'pendingJoin',
  UPDATED_AT: 'updatedAt',
  path: (uid: string) => `users/${uid}`,
  pendingJoin: (p: PendingJoin) => ({ householdId: p.householdId, householdName: p.householdName, code: p.code }),
};

function toProfile(snapshot: DocumentSnapshot): UserProfile {
  const data = snapshot.data() ?? {};
  const pending = data[UserFields.PENDING_JOIN] as Record<string, unknown> | null | undefined;
  const householdId = typeof pending?.householdId === 'string' ? pending.householdId : null;
  const code = typeof pending?.code === 'string' ? pending.code : null;
  return {
    uid: snapshot.id,
    displayName: typeof data.displayName === 'string' ? data.displayName : '',
    householdId: typeof data.householdId === 'string' ? data.householdId : null,
    pendingJoin:
      householdId && code
        ? { householdId, householdName: typeof pending?.householdName === 'string' ? pending.householdName : '', code }
        : null,
    units: data.units === 'metric' || data.units === 'imperial' ? data.units : ProfileDefaults.UNITS,
  };
}

const sameProfile = (a: UserProfile | null, b: UserProfile | null) => JSON.stringify(a) === JSON.stringify(b);

export class FirestoreUserRepository implements UserRepository {
  constructor(private readonly sdk: FirebaseSdk) {}

  observeProfile(uid: string): Observable<UserProfile | null> {
    return this.sdk.instance$.pipe(
      switchMap((db) => docSnapshots(doc(db, UserFields.path(uid)), true)),
      // A cache miss is not proof of absence; wait for the server.
      filter((snapshot) => snapshot.exists() || !snapshot.metadata.fromCache),
      map((snapshot) => (snapshot.exists() ? toProfile(snapshot) : null)),
      distinctUntilChanged(sameProfile),
    );
  }

  async createProfileIfMissing(uid: string, displayName: string): Promise<void> {
    const db = this.sdk.current;
    const ref = doc(db, UserFields.path(uid));
    await mapped(
      runTransaction(db, async (tx) => {
        if ((await tx.get(ref)).exists()) return;
        tx.set(ref, {
          displayName,
          householdId: null,
          pendingJoin: null,
          timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
          locale: ProfileDefaults.LOCALE,
          units: ProfileDefaults.UNITS,
          notificationPrefs: {
            enabled: true,
            digestMinutes: ProfileDefaults.DIGEST_MINUTES,
            stages: [...ProfileDefaults.STAGES],
            expiredMode: ProfileDefaults.EXPIRED_MODE,
          },
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        });
      }),
    );
  }

  async clearHousehold(uid: string): Promise<void> {
    const db = this.sdk.current;
    const batch = writeBatch(db);
    batch.update(doc(db, UserFields.path(uid)), { householdId: null, updatedAt: serverTimestamp() });
    await commitQueued(batch);
  }

  async setUnits(uid: string, units: Units): Promise<void> {
    const db = this.sdk.current;
    const batch = writeBatch(db);
    batch.update(doc(db, UserFields.path(uid)), { units, updatedAt: serverTimestamp() });
    await commitQueued(batch);
  }

  async deleteProfile(uid: string): Promise<void> {
    const db = this.sdk.current;
    const devices = await mapped(getDocsFromServer(collection(db, `${UserFields.path(uid)}/devices`)));
    const batch = writeBatch(db);
    devices.docs.forEach((device) => batch.delete(device.ref));
    batch.delete(doc(db, UserFields.path(uid)));
    await commitConfirmed(batch);
  }
}
