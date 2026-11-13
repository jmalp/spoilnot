// Reminder settings and this phone's push registration (DESIGN 10.2, 18.4), ported from NotificationModels.kt and
// DeviceSync.kt.
import { concatMap, distinctUntilChanged, filter, map, type Observable, type Subscription } from 'rxjs';
import type { DeviceRegistry } from './household';
import type { StockBatch } from './pantry/models';
import { naturalOrder } from './pantry/rules';
import type { Session } from './session';
import { addDays, daysBetween, type LocalDate } from './time';

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
};

/**
 * What this phone can do about notifications (`PushStatus` in core/push): the system's answer, `install` in Safari
 * outside the Home Screen (iOS shows web notifications only in the installed app), or `unsupported`.
 */
export type PushPermission = 'granted' | 'default' | 'denied' | 'install' | 'unsupported';

/** Whether reminders can reach this person on this phone, as the prompt rule sees it (ReminderAccess in the app). */
export type ReminderAccess = {
  permission: PushPermission;
  /** The member's daily reminder (`notificationPrefs.enabled`); null while it loads. */
  remindersOn: boolean | null;
  /** This phone has explained reminders once, answered or not needed. Kept in localStorage, not in Firestore. */
  explained: boolean;
};

/**
 * When this phone explains reminders and asks for the permission, and when Home says they are off (DESIGN 5.4, 18.4;
 * after v1, 2026-11-13, review UX-02 and CODE-10), ported from NotificationPrompt. The rule used to sit in Pantry's Add
 * item, so a member who joined, or anyone on a new phone, was never asked.
 *
 * - A household that **opens with dated food** asks at once: a member who joined, or a new phone.
 * - A household that opens with none (a new one) asks once it has [DATED_TO_ASK] dated items, whoever added them.
 * - It asks only while the member's daily reminder is on, and only once per phone.
 *
 * Where the web differs (DECISIONS 2026-11-13): it asks only while the system can still ask (`default`), so Safari
 * outside the Home Screen waits for the installed app; a phone whose answer is already in (`granted` or `denied`: a
 * web app cannot ask again) is marked explained without asking. There is no app tour on the web (18.1) to wait for.
 *
 * Pure: callers pass today's date from the injected clock, so tests fix the day.
 */
export const NotificationPrompt = {
  /** Dated items a household that opened with none needs before the app asks (after v1, 2026-10-19). */
  DATED_TO_ASK: 3,

  /** Dated food reminders can still warn about: purchases in stock that expire today or later. */
  datedFood: (batches: readonly StockBatch[], today: LocalDate): number =>
    batches.filter((b) => b.quantity > 0 && b.expiryDate !== null && b.expiryDate >= today).length,

  /**
   * Whether to explain reminders and ask now. [datedAtOpen] is the dated food in the first pantry read from the
   * server since the household opened in this run of the app (null until it arrives, so a phone's empty cache is not
   * mistaken for a new household); [datedNow] is the dated food now.
   */
  shouldAsk: (access: ReminderAccess, datedAtOpen: number | null, datedNow: number): boolean =>
    !access.explained &&
    access.permission === 'default' &&
    access.remindersOn === true &&
    datedAtOpen !== null &&
    (datedAtOpen > 0 || datedNow >= NotificationPrompt.DATED_TO_ASK),

  /**
   * The system already answered: remember the explanation as not needed, so a later block shows Home's line. On the
   * web a refusal counts too, since a web app cannot show the system's question again.
   */
  explainedWithoutAsking: (access: ReminderAccess): boolean =>
    !access.explained && (access.permission === 'granted' || access.permission === 'denied'),

  /**
   * Home's "Reminders are off on this phone · Turn on": the member wants reminders, this phone blocks them, and the
   * explanation was already answered (before that, the explanation itself asks). Safari outside the Home Screen has
   * Home's Add to Home Screen card instead, and a browser without web push has nothing to turn on.
   */
  offOnThisPhone: (access: ReminderAccess): boolean =>
    access.explained &&
    (access.permission === 'default' || access.permission === 'denied') &&
    access.remindersOn === true,

  /** Settings' Notifications row: "Off on this phone" when the reminder is on but this phone can't show it. */
  blockedHere: (permission: PushPermission, remindersOn: boolean | null): boolean =>
    permission !== 'granted' && remindersOn === true,
};

/**
 * The local "You're set" notification shown right after Turn on (DESIGN 5.4; owner's decision 2, 2026-11-08, review
 * UX-03), ported from YoureSet: when reminders come, and what tomorrow's reminder would name. Tomorrow's items are what
 * the member's own stages pick for tomorrow (3, 2, 1 or 0 days before), soonest first, one name per product; expired
 * food is left out, since whether it is named again depends on what was already reported.
 */
export type YoureSet = {
  /** The member's reminder hour, 0 to 23. */
  hour: number;
  /** Up to YoureSet.NAMES product names, soonest first. */
  tomorrow: string[];
  /** Products tomorrow's reminder names beyond [tomorrow]. */
  more: number;
};

export const YoureSet = {
  NAMES: 3,

  /** Pure: [today] comes from the injected clock. */
  of(prefs: NotificationPrefs, batches: readonly StockBatch[], today: LocalDate): YoureSet {
    const tomorrow = addDays(today, 1);
    const due = batches
      .filter(
        (b): b is StockBatch & { expiryDate: LocalDate } =>
          b.quantity > 0 && b.expiryDate !== null && prefs.stages.includes(daysBetween(tomorrow, b.expiryDate)),
      )
      .sort(
        (a, b) =>
          (a.expiryDate < b.expiryDate ? -1 : a.expiryDate > b.expiryDate ? 1 : 0) ||
          naturalOrder(a.productName, b.productName),
      );
    const seen = new Set<string>();
    const names = due.filter((b) => !seen.has(b.productId) && seen.add(b.productId)).map((b) => b.productName);
    return {
      hour: NotificationRules.hour(prefs),
      tomorrow: names.slice(0, YoureSet.NAMES),
      more: Math.max(0, names.length - YoureSet.NAMES),
    };
  },
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
