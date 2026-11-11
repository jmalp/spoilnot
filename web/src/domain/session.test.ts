// Ported from SessionManagerTest.
import { BehaviorSubject, combineLatest, defer, filter, map, type Observable } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppError } from '../core/errors';
import type { AuthRepository, AuthUser, PendingJoin, UserProfile, UserRepository } from './account';
import type {
  DeviceRegistry,
  HouseholdRepository,
  JoinRequest,
  JoinRequestStatus,
  LocalData,
  Membership,
} from './household';
import { DeviceSync, type PushIdentity, type StoredFlag } from './notifications';
import { LOADING, SessionManager, SIGNED_OUT, type Session } from './session';

const sam: AuthUser = { uid: 'sam', email: 'sam@example.com', displayName: 'Sam', emailVerified: false };
const pending: PendingJoin = { householdId: 'h1', householdName: 'The Smiths', code: 'ABCDE23456' };
const profile = (householdId: string | null, pendingJoin: PendingJoin | null = null): UserProfile => ({
  uid: 'sam',
  displayName: 'Sam',
  householdId,
  pendingJoin,
  units: 'imperial',
});
const request = (status: JoinRequestStatus): JoinRequest => ({
  uid: 'sam',
  displayName: 'Sam',
  code: pending.code,
  status,
  requestedAt: null,
  emailMasked: null,
});

/** Lets promises and subscriptions settle (advanceUntilIdle). */
const settle = () => new Promise((resolve) => setTimeout(resolve, 0));
/** How long a lost household pointer waits before the gate, in these tests. */
const POINTER_MS = 10;
const settlePointer = () => new Promise((resolve) => setTimeout(resolve, POINTER_MS * 3));

class FakeAuth implements AuthRepository {
  readonly user = new BehaviorSubject<AuthUser | null>(null);
  signUpResult: AuthUser | null = null;
  readonly authUser$ = this.user.asObservable();
  constructor(private readonly log: string[]) {}
  currentUser = () => this.user.value;
  signUp = async () => this.user.next(this.signUpResult);
  signIn = async () => {
    if (this.signInResult) this.user.next(this.signInResult);
  };
  sendPasswordReset = async () => undefined;
  sendEmailVerification = async () => undefined;
  reload = async () => this.user.value;
  /** The account signIn signs in to (null keeps the current user). */
  signInResult: AuthUser | null = null;
  verifyPassword = async (password: string) => {
    if (password !== 'password1') throw new AppError('invalidCredentials');
  };
  reauthenticate = (password: string) => this.verifyPassword(password);
  signOut = async () => {
    this.log.push('signOut');
    this.user.next(null);
  };
  deleteAccount = async () => {
    this.log.push('delete');
    this.user.next(null);
  };
}

/** Server state of users/{uid}; null means the document does not exist. */
class FakeUsers implements UserRepository {
  private readonly docs = new Map<string, BehaviorSubject<UserProfile | null>>();
  readonly deleted = new Set<string>();
  doc(uid: string) {
    if (!this.docs.has(uid)) this.docs.set(uid, new BehaviorSubject<UserProfile | null>(null));
    return this.docs.get(uid)!;
  }
  put = (p: UserProfile) => this.doc(p.uid).next(p);
  profile = (uid: string) => this.doc(uid).value;
  /** When set, the profile listener fails with it, as a refusal from the server would (again on each retry). */
  readonly failure = new BehaviorSubject<unknown>(null);
  /** How many times the profile listener was started. */
  listens = 0;
  observeProfile = (uid: string) =>
    defer(() => {
      this.listens++;
      return combineLatest([this.doc(uid), this.failure]).pipe(
        map(([p, error]) => {
          if (error) throw error;
          return p;
        }),
      );
    });
  createProfileIfMissing = async (uid: string, displayName: string) => {
    if (this.doc(uid).value === null) {
      this.doc(uid).next({ uid, displayName, householdId: null, pendingJoin: null, units: 'imperial' });
    }
  };
  clearHousehold = async (uid: string) => {
    const p = this.doc(uid).value;
    this.doc(uid).next(p && { ...p, householdId: null });
  };
  deleteProfile = async (uid: string) => {
    this.deleted.add(uid);
    this.doc(uid).next(null);
  };
  setUnits = async () => undefined;
}

const unused = () => {
  throw new Error('unused');
};

class FakeHouseholds implements Partial<HouseholdRepository> {
  private readonly memberships = new Map<string, BehaviorSubject<Membership | null>>();
  readonly ownRequest = new BehaviorSubject<JoinRequest | null>(null);
  constructor(private readonly users: FakeUsers) {}
  membership(hid: string, uid: string) {
    const key = `${hid}/${uid}`;
    if (!this.memberships.has(key)) this.memberships.set(key, new BehaviorSubject<Membership | null>(null));
    return this.memberships.get(key)!;
  }
  /** When set, the membership listener fails with it. */
  readonly membershipFailure = new BehaviorSubject<unknown>(null);
  observeMembership = (hid: string, uid: string): Observable<Membership> =>
    combineLatest([
      this.membership(hid, uid).pipe(filter((m): m is Membership => m !== null)),
      this.membershipFailure,
    ]).pipe(
      map(([m, error]) => {
        if (error) throw error;
        return m;
      }),
    );
  observeOwnJoinRequest = () => this.ownRequest.pipe(map((r) => r));
  completeJoin = async (uid: string, _name: string, p: PendingJoin) => {
    this.users.put({ ...this.users.profile(uid)!, householdId: p.householdId, pendingJoin: null });
    this.ownRequest.next(null);
  };
  abandonJoin = async (uid: string) => {
    this.users.put({ ...this.users.profile(uid)!, pendingJoin: null });
    this.ownRequest.next(null);
  };
  leave = async (hid: string, uid: string) => {
    this.membership(hid, uid).next({ kind: 'gone' });
    await this.users.clearHousehold(uid);
  };
}

const noDevices: DeviceRegistry = { sync: async () => undefined, unregister: async () => undefined };

describe('SessionManager', () => {
  let events: string[];
  let auth: FakeAuth;
  let users: FakeUsers;
  let households: FakeHouseholds;
  let clears: number;
  let synced: boolean;
  let localData: LocalData;

  beforeEach(() => {
    events = [];
    auth = new FakeAuth(events);
    users = new FakeUsers();
    households = new FakeHouseholds(users);
    clears = 0;
    synced = true;
    localData = {
      clear: async () => {
        clears++;
        events.push('clear');
      },
      flushPendingWrites: async () => synced,
    };
  });

  const manager = async () => {
    const repo = new Proxy(households, { get: (t, k) => (k in t ? t[k as keyof FakeHouseholds] : unused) });
    const m = new SessionManager(auth, users, repo as unknown as HouseholdRepository, localData, noDevices, POINTER_MS);
    await settle();
    return m;
  };

  it('signed out without an account', async () => {
    expect((await manager()).session).toEqual(SIGNED_OUT);
  });

  it('a missing profile is created with the sign-up name, then the gate is shown', async () => {
    const m = await manager();
    auth.signUpResult = { ...sam, displayName: null };
    await m.signUp(' Samantha ', 'sam@example.com', 'password1');
    await settle();
    expect(users.profile('sam')?.displayName).toBe('Samantha');
    expect(m.session).toEqual({
      kind: 'noHousehold',
      user: { ...sam, displayName: null },
      displayName: 'Samantha',
      join: null,
    });
  });

  it('a member of a household is in it with their role', async () => {
    users.put(profile('h1'));
    households.membership('h1', 'sam').next({ kind: 'active', role: 'owner' });
    auth.user.next(sam);
    const m = await manager();
    expect(m.session).toEqual({
      kind: 'inHousehold',
      user: sam,
      displayName: 'Sam',
      householdId: 'h1',
      role: 'owner',
      units: 'imperial',
    });
    expect(clears).toBe(0);
  });

  it('losing the membership returns to the gate and clears local data once', async () => {
    users.put(profile('h1'));
    households.membership('h1', 'sam').next({ kind: 'active', role: 'member' });
    auth.user.next(sam);
    const m = await manager();
    households.membership('h1', 'sam').next({ kind: 'gone' });
    await settlePointer();
    expect(m.session).toEqual({ kind: 'noHousehold', user: sam, displayName: 'Sam', join: null });
    expect(users.profile('sam')?.householdId).toBeNull();
    expect(clears).toBe(1);
  });

  it('a stale copy of the profile without the household for a moment neither shows the gate nor clears data', async () => {
    users.put(profile('h1'));
    households.membership('h1', 'sam').next({ kind: 'active', role: 'owner' });
    auth.user.next(sam);
    const m = await manager();
    users.put(profile(null));
    await settle();
    users.put(profile('h1'));
    await settlePointer();
    expect(m.session.kind).toBe('inHousehold');
    expect(clears).toBe(0);
  });

  it('a membership already gone at start still clears the stale pointer and cache', async () => {
    users.put(profile('h1'));
    households.membership('h1', 'sam').next({ kind: 'gone' });
    auth.user.next(sam);
    const m = await manager();
    await settlePointer();
    expect(m.session).toEqual({ kind: 'noHousehold', user: sam, displayName: 'Sam', join: null });
    expect(users.profile('sam')?.householdId).toBeNull();
    expect(clears).toBe(1);
  });

  it('leaving returns to the gate and clears local data once', async () => {
    users.put(profile('h1'));
    households.membership('h1', 'sam').next({ kind: 'active', role: 'member' });
    auth.user.next(sam);
    const m = await manager();
    await households.leave('h1', 'sam');
    await settlePointer();
    expect(m.session).toEqual({ kind: 'noHousehold', user: sam, displayName: 'Sam', join: null });
    expect(clears).toBe(1);
  });

  it('a waiting, declined or vanished join request is reported', async () => {
    users.put(profile(null, pending));
    households.ownRequest.next(request('pending'));
    auth.user.next(sam);
    const m = await manager();
    const join = () => (m.session.kind === 'noHousehold' ? m.session.join : undefined);
    expect(join()).toEqual({ kind: 'waiting', pending });
    households.ownRequest.next(request('declined'));
    await settle();
    expect(join()).toEqual({ kind: 'declined', pending });
    // Not a member: the server refuses the membership read, which the repository reports as gone.
    households.membership('h1', 'sam').next({ kind: 'gone' });
    households.ownRequest.next(null);
    await settle();
    expect(join()).toEqual({ kind: 'expired', pending });
  });

  it('an approved request pruned before this phone opened still completes the join', async () => {
    // The owner approved (the membership exists) and the job deleted the request before Sam opened the app.
    users.put(profile(null, pending));
    households.membership('h1', 'sam').next({ kind: 'active', role: 'member' });
    households.ownRequest.next(null);
    auth.user.next(sam);
    const m = await manager();
    await settle();
    expect(m.session).toMatchObject({ kind: 'inHousehold', householdId: 'h1', role: 'member' });
    expect(users.profile('sam')).toEqual(profile('h1'));
  });

  it('sign-out with unsynced changes asks first, and signs out when told to', async () => {
    users.put(profile('h1'));
    households.membership('h1', 'sam').next({ kind: 'active', role: 'member' });
    auth.user.next(sam);
    const m = await manager();
    synced = false;
    expect(await m.signOut()).toBe(false);
    await settle();
    expect(m.session.kind).toBe('inHousehold');
    expect(events).toEqual([]);
    expect(await m.signOut(true)).toBe(true);
    await settle();
    expect(m.session).toEqual(SIGNED_OUT);
    expect(events).toEqual(['clear', 'signOut']);
  });

  it('a profile deleted on another phone is not recreated, and this phone signs out', async () => {
    users.put(profile('h1'));
    households.membership('h1', 'sam').next({ kind: 'active', role: 'member' });
    auth.user.next(sam);
    const m = await manager();
    // Delete account on the other phone: the profile goes first, then the account; this phone's sign-in works.
    users.doc('sam').next(null);
    await settlePointer();
    expect(users.profile('sam')).toBeNull();
    expect(m.session).toEqual(SIGNED_OUT);
    expect(events).toEqual(['clear', 'signOut']);
  });

  it('after an interrupted sign-up the next start signs out, and signing in creates the profile', async () => {
    // Signed in, no profile, and no sign-up or sign-in on this page: as after a deletion elsewhere.
    auth.user.next(sam);
    const m = await manager();
    await settle();
    expect(users.profile('sam')).toBeNull();
    expect(m.session).toEqual(SIGNED_OUT);
    auth.signInResult = sam;
    await m.signIn('sam@example.com', 'password1');
    await settle();
    expect(users.profile('sam')?.displayName).toBe('Sam');
    expect(m.session).toEqual({ kind: 'noHousehold', user: sam, displayName: 'Sam', join: null });
  });

  it('after sign-in a profile deleted later is not recreated', async () => {
    users.put(profile(null));
    const m = await manager();
    auth.signInResult = sam;
    await m.signIn('sam@example.com', 'password1');
    await settle();
    expect(m.session.kind).toBe('noHousehold');
    users.doc('sam').next(null);
    await settle();
    expect(users.profile('sam')).toBeNull();
    expect(m.session).toEqual(SIGNED_OUT);
  });

  it('sign-up creates the profile', async () => {
    const m = await manager();
    auth.signUpResult = sam;
    await m.signUp('Sam', 'sam@example.com', 'password1');
    await settle();
    expect(users.profile('sam')?.displayName).toBe('Sam');
  });

  it('an approved request completes on its own and enters the household', async () => {
    users.put(profile(null, pending));
    households.ownRequest.next(request('pending'));
    auth.user.next(sam);
    const m = await manager();
    households.membership('h1', 'sam').next({ kind: 'active', role: 'member' });
    households.ownRequest.next(request('approved'));
    await settle();
    expect(m.session).toMatchObject({ kind: 'inHousehold', householdId: 'h1', role: 'member' });
    expect(users.profile('sam')).toEqual(profile('h1'));
    expect(clears).toBe(0);
  });

  describe('when a listener fails', () => {
    const inHousehold = (role: 'owner' | 'member'): Session => ({
      kind: 'inHousehold',
      user: sam,
      displayName: 'Sam',
      householdId: 'h1',
      role,
      units: 'imperial',
    });
    /** Every session the manager shows, in order. */
    const record = (m: SessionManager) => {
      const seen: Session[] = [];
      const subscription = m.session$.subscribe((s) => seen.push(s));
      return { seen, stop: () => subscription.unsubscribe() };
    };
    afterEach(() => {
      vi.useRealTimers();
    });

    it('the quota running out keeps the household on screen while the session listens again', async () => {
      users.put(profile('h1'));
      households.membership('h1', 'sam').next({ kind: 'active', role: 'member' });
      auth.user.next(sam);
      const m = await manager();
      const { seen, stop } = record(m);
      vi.useFakeTimers();
      const listens = users.listens;

      users.failure.next(new AppError('quotaExceeded'));
      // Retries after 1, 2, 4 … 32 s, without Loading in between.
      await vi.advanceTimersByTimeAsync(70_000);
      expect(users.listens - listens).toBeGreaterThanOrEqual(5);
      expect(m.session).toEqual(inHousehold('member'));

      users.failure.next(null);
      await vi.advanceTimersByTimeAsync(40_000);
      expect(seen).toEqual([inHousehold('member')]);
      expect(clears).toBe(0);
      expect(events).toEqual([]);
      stop();
    });

    it('a failed membership listener keeps the household whatever the error, except a refusal', async () => {
      users.put(profile('h1'));
      households.membership('h1', 'sam').next({ kind: 'active', role: 'owner' });
      auth.user.next(sam);
      const m = await manager();
      const { seen, stop } = record(m);
      vi.useFakeTimers();

      for (const error of [new AppError('quotaExceeded'), new AppError('offline'), new TypeError('bad field')]) {
        households.membershipFailure.next(error);
        await vi.advanceTimersByTimeAsync(5_000);
        expect(m.session).toEqual(inHousehold('owner'));
        households.membershipFailure.next(null);
        await vi.advanceTimersByTimeAsync(40_000);
      }
      expect(seen).toEqual([inHousehold('owner')]);
      expect(clears).toBe(0);
      stop();
    });

    it('the gate and its join request stay on screen when the quota runs out', async () => {
      users.put(profile(null, pending));
      households.ownRequest.next(request('pending'));
      auth.user.next(sam);
      const m = await manager();
      vi.useFakeTimers();

      users.failure.next(new AppError('quotaExceeded'));
      await vi.advanceTimersByTimeAsync(10_000);
      expect(m.session).toEqual({
        kind: 'noHousehold',
        user: sam,
        displayName: 'Sam',
        join: { kind: 'waiting', pending },
      });
    });

    it('a permission refusal shows Loading until the profile can be read again', async () => {
      users.put(profile('h1'));
      households.membership('h1', 'sam').next({ kind: 'active', role: 'member' });
      auth.user.next(sam);
      const m = await manager();
      vi.useFakeTimers();

      users.failure.next(new AppError('permissionDenied'));
      await vi.advanceTimersByTimeAsync(0);
      expect(m.session).toEqual(LOADING);

      users.failure.next(null);
      await vi.advanceTimersByTimeAsync(40_000);
      expect(m.session).toEqual(inHousehold('member'));
    });
  });

  it('sign-out clears local data before signing out', async () => {
    users.put(profile('h1'));
    households.membership('h1', 'sam').next({ kind: 'active', role: 'member' });
    auth.user.next(sam);
    const m = await manager();
    await m.signOut();
    await settle();
    expect(m.session).toEqual(SIGNED_OUT);
    expect(events).toEqual(['clear', 'signOut']);
  });

  it('an owner cannot delete their account', async () => {
    users.put(profile('h1'));
    households.membership('h1', 'sam').next({ kind: 'active', role: 'owner' });
    auth.user.next(sam);
    const m = await manager();
    await expect(m.deleteAccount('password1')).rejects.toMatchObject({ kind: 'ownerMustTransferFirst' });
    expect(auth.user.value).toEqual(sam);
    expect(users.profile('sam')).not.toBeNull();
  });

  it('a wrong password deletes nothing', async () => {
    users.put(profile(null));
    auth.user.next(sam);
    const m = await manager();
    await expect(m.deleteAccount('wrong')).rejects.toMatchObject({ kind: 'invalidCredentials' });
    expect(users.profile('sam')).not.toBeNull();
  });

  it("deleting a member's account leaves, deletes the profile, clears data and deletes the sign-in", async () => {
    users.put(profile('h1'));
    households.membership('h1', 'sam').next({ kind: 'active', role: 'member' });
    auth.user.next(sam);
    const m = await manager();
    await m.deleteAccount('password1');
    await settle();
    expect(households.membership('h1', 'sam').value).toEqual({ kind: 'gone' });
    expect(users.deleted.has('sam')).toBe(true);
    expect(clears).toBe(1);
    expect(m.session).toEqual(SIGNED_OUT);
    // The profile must not be recreated while the account is being deleted.
    expect(users.profile('sam')).toBeNull();
  });
});

// Ported from DeviceSyncTest: the session and this browser's push token together.
describe('DeviceSync with the session', () => {
  let events: string[];
  let auth: FakeAuth;
  let users: FakeUsers;
  let households: FakeHouseholds;
  let online: boolean;
  let flag: boolean;

  /** A fixed push identity, as on a phone; offline its token delete fails. */
  const push: PushIdentity = {
    installId: () => 'browser-1',
    token: async () => 'token-1',
    deleteToken: async () => {
      events.push(online ? 'deleteToken' : 'deleteToken failed');
      if (!online) throw new AppError('offline');
    },
  };
  const tokenToDelete: StoredFlag = { get: () => flag, set: (on) => void (flag = on) };
  const devices: DeviceRegistry = {
    sync: async (uid) => void events.push(`sync ${uid}`),
    unregister: async (uid) => void events.push(`unregister ${uid}`),
  };
  const localData: LocalData = { clear: async () => void events.push('clear'), flushPendingWrites: async () => true };

  beforeEach(() => {
    events = [];
    auth = new FakeAuth(events);
    users = new FakeUsers();
    households = new FakeHouseholds(users);
    online = true;
    flag = false;
  });

  const started = async () => {
    const repo = new Proxy(households, { get: (t, k) => (k in t ? t[k as keyof FakeHouseholds] : unused) });
    const m = new SessionManager(auth, users, repo as unknown as HouseholdRepository, localData, devices, POINTER_MS);
    const sync = new DeviceSync(m.session$, devices, push, tokenToDelete);
    sync.start();
    await settle();
    /** The page closes. */
    const close = () => {
      sync.stop();
      m.dispose();
    };
    return Object.assign(m, { close });
  };

  const samInHousehold = () => {
    users.put(profile('h1'));
    households.membership('h1', 'sam').next({ kind: 'active', role: 'member' });
  };

  it('a start without a signed-in user and no token to delete loads nothing', async () => {
    await started();
    expect(events).toEqual([]);
  });

  it('a start with a signed-in user registers the phone once', async () => {
    samInHousehold();
    auth.user.next(sam);
    await started();
    expect(events).toEqual(['sync sam']);
  });

  it('signing in registers the phone and signing out deletes the token', async () => {
    const m = await started();
    samInHousehold();
    auth.user.next(sam);
    await settle();
    await m.signOut();
    await settle();
    expect(events).toEqual(['sync sam', 'unregister sam', 'clear', 'signOut', 'deleteToken']);
    expect(flag).toBe(false);
  });

  it('signing out offline keeps the token to delete, and the next start deletes it before anyone registers', async () => {
    samInHousehold();
    auth.user.next(sam);
    const m = await started();
    online = false;
    await m.signOut();
    await settle();
    expect(events).toEqual(['sync sam', 'unregister sam', 'clear', 'signOut', 'deleteToken failed']);
    expect(flag).toBe(true);
    m.close();

    // The next start, online again: Alex signs in on this phone.
    online = true;
    events.length = 0;
    const alex: AuthUser = { ...sam, uid: 'alex', email: 'alex@example.com', displayName: 'Alex' };
    users.put({ ...profile(null), uid: 'alex', displayName: 'Alex' });
    auth.user.next(alex);
    await started();
    expect(events).toEqual(['deleteToken', 'sync alex']);
    expect(flag).toBe(false);
  });

  it('deleting the account deletes the token', async () => {
    samInHousehold();
    auth.user.next(sam);
    const m = await started();
    await m.deleteAccount('password1');
    await settle();
    expect(events).toEqual(['sync sam', 'clear', 'delete', 'deleteToken']);
  });

  it('a deletion that fails after leaving the household is kept for the gate, until dismissed or retried', async () => {
    samInHousehold();
    auth.user.next(sam);
    const m = await started();
    auth.deleteAccount = async () => {
      throw new AppError('offline');
    };
    await expect(m.deleteAccount('password1')).rejects.toMatchObject({ kind: 'offline' });
    await settle();
    expect(m.session.kind).toBe('noHousehold');
    expect(m.deleteFailure).toMatchObject({ kind: 'offline' });
    m.dismissDeleteFailure();
    expect(m.deleteFailure).toBeNull();

    // A wrong password fails before anything starts: the dialog that asked shows it, so nothing is kept.
    await expect(m.deleteAccount('wrong')).rejects.toMatchObject({ kind: 'invalidCredentials' });
    expect(m.deleteFailure).toBeNull();
  });

  it('a deletion that fails while still in the household keeps nothing, as no screen would show it', async () => {
    samInHousehold();
    auth.user.next(sam);
    const m = await started();
    households.leave = async () => {
      throw new AppError('offline');
    };
    await expect(m.deleteAccount('password1')).rejects.toMatchObject({ kind: 'offline' });
    await settle();
    expect(m.session.kind).toBe('inHousehold');
    expect(m.deleteFailure).toBeNull();
  });
});
