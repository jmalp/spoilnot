// The session state machine and membership watcher (DESIGN 5.1, 9.1), ported from SessionManager.
//
// - Creates the profile if it is missing right after sign-up or sign-in on this phone (an interrupted sign-up). A
//   profile that disappears later is never recreated: it was deleted with its account, maybe on another phone, so
//   this phone signs out instead (after v1).
// - Completes an approved join request, also when the request itself is gone but the membership exists.
// - When the membership disappears (removed, or the household was deleted) it clears the profile pointer, which
//   returns the user to the gate; whenever a household is left for any reason the local cache is cleared.
// - Sign-out and account deletion pause all listeners while local data is cleared.
// - A profile that loses its household pointer waits a moment before the gate: the web SDK can raise the server's
//   older copy of the profile between the acknowledgement of this phone's own write and the newer version.
import {
  BehaviorSubject,
  catchError,
  combineLatest,
  concat,
  defer,
  distinctUntilChanged,
  EMPTY,
  filter,
  first,
  firstValueFrom,
  from,
  ignoreElements,
  map,
  of,
  shareReplay,
  switchMap,
  tap,
  timeout,
  timer,
  type Observable,
  type Subscription,
} from 'rxjs';
import { AppError, isAppError } from '../core/errors';
import {
  AccountValidation,
  type AuthRepository,
  type AuthUser,
  type PendingJoin,
  type Units,
  type UserProfile,
  type UserRepository,
} from './account';
import type { DeviceRegistry, HouseholdRepository, LocalData, Role } from './household';

/**
 * The user's one join request; an approved request completes on its own. Expired: it disappeared unanswered, checked
 * against the membership, since the job may prune an approved request before this phone finished the join (SYNC-06).
 */
export type JoinState = { kind: 'waiting' | 'declined' | 'expired'; pending: PendingJoin };

/** Where the signed-in user stands; drives the top-level screen (sign-in, household gate or the app). */
export type Session =
  | { kind: 'loading' }
  | { kind: 'signedOut' }
  | { kind: 'noHousehold'; user: AuthUser; displayName: string; join: JoinState | null }
  | { kind: 'inHousehold'; user: AuthUser; displayName: string; householdId: string; role: Role; units: Units };

export const LOADING: Session = { kind: 'loading' };
export const SIGNED_OUT: Session = { kind: 'signedOut' };

const noHousehold = (user: AuthUser, displayName: string, join: JoinState | null): Session => ({
  kind: 'noHousehold',
  user,
  displayName,
  join,
});

/** Decide from a settled session, not a momentary Loading while listeners reconnect. */
const SETTLE_TIMEOUT_MS = 10_000;
/** How long a lost household pointer must stay lost before the gate (the stale copy is replaced within moments). */
export const POINTER_SETTLE_MS = 1_000;
const backoffMs = (attempt: number) => 1_000 * 2 ** Math.min(attempt, 5);

export class SessionManager {
  private readonly shuttingDown = new BehaviorSubject(false);
  private pendingDisplayName: string | null = null;
  /**
   * Set when someone signs up or signs in on this phone, until their profile is seen: only then may a missing profile
   * be created (after v1). Set before the call, since the profile listener can start before it returns.
   */
  private mayCreateProfile = false;
  /** The household this page last saw the user in; its cache is cleared once they are no longer in it. */
  private lastHouseholdId: string | null = null;
  private current: Session = LOADING;
  private readonly subscription: Subscription;
  /** See [deleteFailure]. */
  private failedDeletion: unknown = null;

  readonly session$: Observable<Session>;

  constructor(
    private readonly auth: AuthRepository,
    private readonly users: UserRepository,
    private readonly households: HouseholdRepository,
    private readonly localData: LocalData,
    private readonly devices: DeviceRegistry,
    private readonly pointerSettleMs = POINTER_SETTLE_MS,
  ) {
    this.session$ = combineLatest([
      auth.authUser$.pipe(distinctUntilChanged((a, b) => a?.uid === b?.uid)),
      this.shuttingDown,
    ]).pipe(
      switchMap(([user, busy]) => {
        if (busy) return of(LOADING);
        if (user === null) return of(SIGNED_OUT);
        return this.sessionFor(user);
      }),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
      tap((session) => this.onTransition(session)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    // Started at once, like stateIn(Eagerly): the session is known before any screen asks.
    this.subscription = this.session$.subscribe((session) => (this.current = session));
  }

  get session(): Session {
    return this.current;
  }

  /**
   * Why the last Delete account failed after it had started deleting, or null. By then Loading has replaced the
   * screen that asked, so the household gate shows this when the failed deletion leaves the user there (it had
   * already left the household, or never had one). Cleared by the next attempt, [dismissDeleteFailure], or a session
   * in a household or signed out, where no screen would show it.
   */
  get deleteFailure(): unknown {
    return this.failedDeletion;
  }

  dismissDeleteFailure(): void {
    this.failedDeletion = null;
  }

  dispose(): void {
    this.subscription.unsubscribe();
  }

  async signUp(displayName: string, email: string, password: string): Promise<void> {
    this.pendingDisplayName = displayName.trim();
    this.mayCreateProfile = true;
    await this.auth.signUp(displayName.trim(), email.trim(), password);
  }

  signIn(email: string, password: string): Promise<void> {
    this.mayCreateProfile = true;
    return this.auth.signIn(email.trim(), password);
  }

  /**
   * Signs out, unless this phone has changes that haven't reached the server yet and [evenIfUnsynced] is false:
   * signing out clears the phone's copy, queue included, so it returns false and the screen asks "Some changes
   * haven't synced yet. Sign out anyway?" first (after v1, SYNC-09). True once signed out.
   */
  async signOut(evenIfUnsynced = false): Promise<boolean> {
    if (!evenIfUnsynced && !(await this.localData.flushPendingWrites())) return false;
    await this.signOutNow();
    return true;
  }

  /** Clears this device's copy of the household before signing out (DESIGN 12.2, control 6). */
  private async signOutNow(): Promise<void> {
    const uid = this.auth.currentUser()?.uid;
    this.shuttingDown.next(true);
    try {
      // This phone stops receiving the user's reminders (DESIGN 10.2).
      if (uid) await this.devices.unregister(uid).catch(() => undefined);
      await this.localData.clear().catch(() => undefined);
      await this.auth.signOut();
    } finally {
      this.pendingDisplayName = null;
      this.lastHouseholdId = null;
      this.shuttingDown.next(false);
    }
  }

  /**
   * Deletes the account (DESIGN 5.1): checks the password, leaves the household, deletes the profile and devices,
   * clears local data and deletes the sign-in. An owner must transfer ownership or delete the household first.
   * The sign-in is refreshed only at the very end if Firebase requires it, after all Firestore writes.
   */
  async deleteAccount(password: string): Promise<void> {
    this.failedDeletion = null;
    const uid = this.auth.currentUser()?.uid;
    if (!uid) return;
    const current = await firstValueFrom(
      this.session$.pipe(
        filter((s) => s.kind !== 'loading'),
        timeout(SETTLE_TIMEOUT_MS),
      ),
    ).catch(() => {
      throw new AppError('offline');
    });
    if (current.kind === 'inHousehold' && current.role === 'owner') throw new AppError('ownerMustTransferFirst');
    await this.auth.verifyPassword(password);
    this.shuttingDown.next(true);
    try {
      if (current.kind === 'inHousehold') await this.households.leave(current.householdId, uid);
      if (current.kind === 'noHousehold' && current.join) await this.households.abandonJoin(uid, current.join.pending);
      await this.users.deleteProfile(uid);
      await this.localData.clear().catch(() => undefined);
      try {
        await this.auth.deleteAccount();
      } catch (error) {
        if (!isAppError(error, 'recentLoginRequired')) throw error;
        // Firestore work is finished, so refreshing the sign-in now cannot affect it.
        await this.auth.reauthenticate(password);
        await this.auth.deleteAccount();
      }
    } catch (error) {
      // The sign-in still exists: its profile may be created again, so the gate can show this.
      this.mayCreateProfile = true;
      this.failedDeletion = error ?? new AppError('unknown');
      throw error;
    } finally {
      this.pendingDisplayName = null;
      this.lastHouseholdId = null;
      this.shuttingDown.next(false);
    }
  }

  private sessionFor(user: AuthUser): Observable<Session> {
    let hadHousehold = false;
    const source = this.users.observeProfile(user.uid).pipe(
      switchMap((profile): Observable<Session> => {
        if (profile !== null) this.mayCreateProfile = false;
        const lostHousehold = hadHousehold && profile?.householdId == null;
        hadHousehold = profile?.householdId != null;
        if (lostHousehold) {
          // A pointer that comes right back (a stale copy of the profile) never shows the gate or clears the cache.
          return timer(this.pointerSettleMs).pipe(switchMap(() => this.profileSession(user, profile)));
        }
        return this.profileSession(user, profile);
      }),
    );
    return withRetry(source);
  }

  private profileSession(user: AuthUser, profile: UserProfile | null): Observable<Session> {
    if (profile === null) {
      return concat(
        of(LOADING),
        defer(async () => {
          // Only right after sign-up or sign-in on this phone (an interrupted sign-up then needs a sign-in again). A
          // profile that disappears later was deleted with its account, maybe on another phone, whose sign-in is
          // deleted only after the profile: it must not come back (after v1). Sign out here too.
          if (this.mayCreateProfile) {
            await this.users.createProfileIfMissing(user.uid, this.displayNameFor(user));
          } else {
            void this.signOutNow().catch(() => undefined);
          }
        }).pipe(ignoreElements()),
      );
    }
    if (profile.householdId !== null) return this.watchMembership(user, profile, profile.householdId);
    if (profile.pendingJoin !== null) return this.watchJoinRequest(user, profile, profile.pendingJoin);
    return of(noHousehold(user, profile.displayName, null));
  }

  private watchMembership(user: AuthUser, profile: UserProfile, householdId: string): Observable<Session> {
    return this.households.observeMembership(householdId, user.uid).pipe(
      switchMap((membership): Observable<Session> => {
        if (membership.kind === 'active') {
          return of({
            kind: 'inHousehold',
            user,
            displayName: profile.displayName,
            householdId,
            role: membership.role,
            units: profile.units,
          });
        }
        // Removed, or the household was deleted. Clearing the pointer moves the profile (and so the session) to
        // the gate within moments; the cache is cleared on that transition.
        if (this.lastHouseholdId === null) this.lastHouseholdId = householdId;
        return from(this.users.clearHousehold(user.uid)).pipe(
          ignoreElements(),
          catchError(() => of(noHousehold(user, profile.displayName, null))),
        );
      }),
    );
  }

  private watchJoinRequest(user: AuthUser, profile: UserProfile, pending: PendingJoin): Observable<Session> {
    return this.households.observeOwnJoinRequest(pending.householdId, user.uid).pipe(
      switchMap((request) => {
        // A missing request is expired only when no membership was made from it: the owner may have approved it
        // and the job pruned it before this phone opened (after v1, SYNC-06).
        const approved$: Observable<boolean> =
          request === null
            ? this.households.observeMembership(pending.householdId, user.uid).pipe(
                first(),
                map((m) => m.kind === 'active'),
              )
            : of(request.status === 'approved');
        return approved$.pipe(
          switchMap((approved) => {
            const kind = approved
              ? 'waiting'
              : request === null
                ? 'expired'
                : request.status === 'declined'
                  ? 'declined'
                  : 'waiting';
            const session = noHousehold(user, profile.displayName, { kind, pending });
            if (!approved) return of(session);
            return concat(
              of(session),
              defer(() => this.households.completeJoin(user.uid, profile.displayName, pending)).pipe(ignoreElements()),
            );
          }),
        );
      }),
    );
  }

  private onTransition(session: Session): void {
    switch (session.kind) {
      case 'inHousehold':
        this.lastHouseholdId = session.householdId;
        this.failedDeletion = null;
        break;
      case 'noHousehold':
        if (this.lastHouseholdId !== null) {
          this.lastHouseholdId = null;
          void this.localData.clear().catch(() => undefined);
        }
        break;
      case 'signedOut':
        this.lastHouseholdId = null;
        this.failedDeletion = null;
        break;
      case 'loading':
        break;
    }
  }

  private displayNameFor(user: AuthUser): string {
    return this.pendingDisplayName ?? (user.displayName?.trim() || AccountValidation.fallbackDisplayName(user.email));
  }
}

/**
 * The server refused the read (`permission-denied` or `unauthenticated`): the rules no longer let this user see the
 * profile or membership, so the screen must not keep showing it.
 */
export const isPermissionRefusal = (error: unknown): boolean => isAppError(error, 'permissionDenied');

/**
 * On an error: a growing pause, then listening again (retryWhen with backoff in the app). Only a permission refusal
 * shows Loading meanwhile; any other error (the quota running out, the network, a bad document) keeps the last
 * session on screen, cached data included (DESIGN 6.7, 7.5; after v1, WEB-04).
 */
function withRetry(source: Observable<Session>, attempt = 0): Observable<Session> {
  return source.pipe(
    catchError((error: unknown) =>
      concat(
        isPermissionRefusal(error) ? of(LOADING) : EMPTY,
        timer(backoffMs(attempt)).pipe(ignoreElements()),
        defer(() => withRetry(source, attempt + 1)),
      ),
    ),
  );
}
