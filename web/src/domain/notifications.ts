// Reminder settings and this phone's push registration (DESIGN 10.2, 18.4), ported from NotificationModels.kt and
// DeviceSync.kt.
import { concatMap, distinctUntilChanged, filter, map, type Observable, type Subscription } from 'rxjs';
import type { DeviceRegistry } from './household';
import type { Session } from './session';

export type ExpiredMode = 'once' | 'daily' | 'never';
export const EXPIRED_MODES: readonly ExpiredMode[] = ['once', 'daily', 'never'];

/** users/{uid}.notificationPrefs (DESIGN 8.2). The UI offers whole hours. */
export type NotificationPrefs = {
  enabled: boolean;
  digestMinutes: number;
  /** Days before expiry, a subset of 3, 2, 1 and 0, largest first. */
  stages: number[];
  expiredMode: ExpiredMode;
  /** The reminder also lists the meals the user cooks that day (DESIGN 5.9). */
  cooking: boolean;
};

export const NotificationRules = {
  DEFAULT_MINUTES: 9 * 60,
  ALL_STAGES: [3, 2, 1, 0] as readonly number[],
  /** The job does not send after 22:00 (DESIGN 10.3), so the last offered hour is 21. */
  HOURS: Array.from({ length: 21 - 5 + 1 }, (_, i) => 5 + i),
  /** The explanation shows once the third dated item is saved (DESIGN 5.4), when reminders have something to say. */
  PROMPT_DATED: 3,

  hour: (prefs: NotificationPrefs) => Math.floor(prefs.digestMinutes / 60),

  /** Stages as stored: known ones, once each, largest first. */
  stages(stages: Iterable<number>): number[] {
    const chosen = new Set(stages);
    return NotificationRules.ALL_STAGES.filter((s) => chosen.has(s));
  },

  /** The stored map, with the app's defaults for anything missing. */
  read(raw: unknown): NotificationPrefs {
    const map = (raw && typeof raw === 'object' ? raw : {}) as Record<string, unknown>;
    const stages = Array.isArray(map.stages)
      ? NotificationRules.stages(map.stages.filter((s): s is number => typeof s === 'number'))
      : [...NotificationRules.ALL_STAGES];
    return {
      enabled: typeof map.enabled === 'boolean' ? map.enabled : true,
      digestMinutes: typeof map.digestMinutes === 'number' ? map.digestMinutes : NotificationRules.DEFAULT_MINUTES,
      stages,
      expiredMode: EXPIRED_MODES.includes(map.expiredMode as ExpiredMode) ? (map.expiredMode as ExpiredMode) : 'once',
      cooking: typeof map.cooking === 'boolean' ? map.cooking : true,
    };
  },

  /**
   * Whether saving an item should explain notifications first: it has a date, it makes at least the third dated
   * item, and the explanation was not shown on this phone yet.
   */
  shouldExplain: (hasDate: boolean, datedBefore: number, shown: boolean) =>
    hasDate && datedBefore + 1 >= NotificationRules.PROMPT_DATED && !shown,
};

export interface NotificationPrefsRepository {
  observe(uid: string): Observable<NotificationPrefs>;
  save(uid: string, prefs: NotificationPrefs): Promise<void>;
}

const signedInUid = (session: Session): string | null =>
  session.kind === 'inHousehold' || session.kind === 'noHousehold' ? session.user.uid : null;

/** This browser's push identity (PushIdentity in the app); `FcmPushIdentity` in src/data, a fake in tests. */
export interface PushIdentity {
  /** The id that names this browser's device document. */
  installId(): string;
  /** The FCM token, or null where this browser cannot get one (no permission, Push API, service worker or key). */
  token(): Promise<string | null>;
  /** Deletes the token, if there is one, so FCM rejects sends to it. Rejects when that could not be done. */
  deleteToken(): Promise<void>;
}

/** A yes or no kept in this browser's own storage, outside Firestore (whose copy sign-out wipes). */
export interface StoredFlag {
  get(): boolean;
  set(on: boolean): void;
}

/** How long a token delete may take before it waits for the next start. */
export const TOKEN_DELETE_TIMEOUT_MS = 5_000;

/**
 * Registers this phone for digests once per signed-in user per app start, and again when the user allows
 * notifications (the app's onNewToken: on the web a token exists only after the permission).
 *
 * When a user leaves this phone (sign-out, account deletion, or Firebase signing them out) it deletes the push
 * token (DESIGN 10.2, 18.4), so a device document the sign-out could not delete holds a token FCM rejects and the
 * reminder job removes. [tokenToDelete] is set first and cleared only once the delete is confirmed; while it is set,
 * every start deletes the token before anything else, so a phone signed out offline stops getting digests at its
 * next start online. A start with nobody signed in and nothing to delete loads nothing. The steps run one at a time,
 * so a sign-in right after a start registers only once the old token is gone.
 */
export class DeviceSync {
  private subscription: Subscription | null = null;
  private uid: string | null = null;

  constructor(
    private readonly session$: Observable<Session>,
    private readonly registry: DeviceRegistry,
    private readonly push: PushIdentity,
    private readonly tokenToDelete: StoredFlag,
    private readonly timeoutMs = TOKEN_DELETE_TIMEOUT_MS,
  ) {}

  start(): void {
    if (this.subscription) return;
    let previous: string | null | undefined;
    this.subscription = this.session$
      .pipe(
        // A Loading moment (listeners reconnecting, a sign-out in progress) is not a change of user.
        filter((session) => session.kind !== 'loading'),
        map(signedInUid),
        distinctUntilChanged(),
        concatMap((uid) => {
          const userLeft = previous != null;
          previous = uid;
          this.uid = uid;
          return this.step(uid, userLeft);
        }),
      )
      .subscribe();
  }

  /** After the user allows notifications. */
  permissionGranted(): Promise<void> {
    return this.uid ? this.registry.sync(this.uid).catch(() => undefined) : Promise.resolve();
  }

  stop(): void {
    this.subscription?.unsubscribe();
    this.subscription = null;
  }

  private async step(uid: string | null, userLeft: boolean): Promise<void> {
    if (userLeft) this.tokenToDelete.set(true);
    if (this.tokenToDelete.get()) await this.deleteToken();
    if (uid !== null) await this.registry.sync(uid).catch(() => undefined);
  }

  private async deleteToken(): Promise<void> {
    let timer: ReturnType<typeof setTimeout> | undefined;
    try {
      await Promise.race([
        this.push.deleteToken(),
        new Promise((_, reject) => {
          timer = setTimeout(() => reject(new Error('timeout')), this.timeoutMs);
        }),
      ]);
      this.tokenToDelete.set(false);
    } catch {
      // Offline, or FCM did not answer in time: the flag stays, and the next start tries again.
    } finally {
      clearTimeout(timer);
    }
  }
}
