// Reminders on this phone, for the whole signed-in app (RemindersViewModel in the app; DESIGN 5.4, 18.4): the
// explanation that asks for the permission on whichever tab is open, Home's "Reminders are off on this phone · Turn
// on", Settings' "Off on this phone" and "You're set" after Turn on. The rule is NotificationPrompt (domain).
import { useCallback, useEffect, useMemo, useState } from 'react';
import { catchError, firstValueFrom, map, of, scan, timeout } from 'rxjs';
import { useObservable, useToday } from '../../core/hooks';
import { plural, str } from '../../core/i18n';
import {
  PUSH_STATUS_EVENT,
  pushStatus,
  requestPushPermission,
  showLocalNotification,
  type PushStatus,
} from '../../core/push';
import { useHouseholdSession, usePreferences, useServices } from '../../core/services';
import { retryLoading } from '../../domain/load';
import {
  NotificationPrompt,
  NotificationRules,
  YoureSet,
  type NotificationPrefs,
  type ReminderAccess,
} from '../../domain/notifications';
import type { StockBatch } from '../../domain/pantry/models';
import { today as todayOf } from '../../domain/time';

/** "9:00 AM" in the phone's language (FormatStyle.SHORT in the app). */
export const formatHour = (hour: number): string =>
  new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(new Date(2000, 0, 1, hour));

/**
 * The system's notification permission, checked again when the user comes back from the iPhone's Settings and
 * whenever the app asked.
 */
export function usePushStatus(): PushStatus {
  const [status, setStatus] = useState(pushStatus);
  useEffect(() => {
    const refresh = () => setStatus(pushStatus());
    document.addEventListener('visibilitychange', refresh);
    window.addEventListener(PUSH_STATUS_EVENT, refresh);
    return () => {
      document.removeEventListener('visibilitychange', refresh);
      window.removeEventListener(PUSH_STATUS_EVENT, refresh);
    };
  }, []);
  return status;
}

/** The member's notification settings (NotificationSettingsViewModel); null while they load. */
export function useNotificationPrefs(): NotificationPrefs | null {
  const me = useHouseholdSession();
  const { notificationPrefs } = useServices();
  return useObservable<NotificationPrefs | null>(
    useMemo(() => notificationPrefs.observe(me.user.uid), [notificationPrefs, me.user.uid]),
    null,
  );
}

/** "Spoil Not reminds you once a day, around 9:00 AM, … Tomorrow's reminder: Spinach, Chicken and 2 more." */
export function youreSetBody(content: YoureSet): string {
  const time = str('notif_youre_set_time', formatHour(content.hour));
  const names = content.tomorrow.join(', ');
  const tomorrow =
    content.tomorrow.length === 0
      ? str('notif_youre_set_nothing')
      : str(
          'notif_youre_set_tomorrow',
          content.more === 0 ? names : plural('notif_youre_set_more', content.more, names, content.more),
        );
  return `${time} ${tomorrow}`;
}

/** How long "You're set" waits for the member's settings and the pantry (YoureSetSender's 5 s). */
const YOURE_SET_WAIT_MS = 5_000;

/**
 * Turn on (after v1, review UX-02, UX-03): asks the system while it still can, registers this phone for digests and
 * shows "You're set" once allowed. When the system no longer asks (refused, or Safari outside the Home Screen),
 * [elsewhere] runs instead: a web app can't open the iPhone's Settings, so Settings > Notifications says where to go.
 */
export function useTurnOnReminders(): (elsewhere?: () => void) => Promise<PushStatus> {
  const me = useHouseholdSession();
  const { deviceSync, notificationPrefs, pantry, preferences, clock } = useServices();
  return useCallback(
    async (elsewhere?: () => void) => {
      // The explanation is answered whatever the system says.
      if (!preferences.get().notificationExplainerShown) preferences.set({ notificationExplainerShown: true });
      if (pushStatus() !== 'default') {
        elsewhere?.();
        return pushStatus();
      }
      const answer = await requestPushPermission();
      if (answer !== 'granted') return answer;
      await deviceSync.permissionGranted();
      const [prefs, batches] = await Promise.all([
        firstValueFrom(
          notificationPrefs.observe(me.user.uid).pipe(
            timeout(YOURE_SET_WAIT_MS),
            catchError(() => of(null)),
          ),
        ),
        firstValueFrom(
          pantry.observe(me.householdId).pipe(
            map((data) => data.batches),
            timeout(YOURE_SET_WAIT_MS),
            catchError(() => of<StockBatch[]>([])),
          ),
        ),
      ]);
      // Nothing to promise when the daily reminder is off.
      if (prefs?.enabled) {
        await showLocalNotification(
          str('notif_youre_set_title'),
          youreSetBody(YoureSet.of(prefs, batches, todayOf(clock))),
          'youre-set',
        );
      }
      return answer;
    },
    [deviceSync, notificationPrefs, pantry, preferences, clock, me.user.uid, me.householdId],
  );
}

/** The batches when the household first loaded from the server in this run of the app, and now. */
type Dated = { atOpen: StockBatch[] | null; now: StockBatch[] };
const NOT_LISTENING: Dated = { atOpen: null, now: [] };

export type Reminders = {
  /** Show the explanation now. */
  ask: boolean;
  /** Home's line: the member wants reminders and this phone blocks them. */
  offOnThisPhone: boolean;
  /** The reminder hour, for the explanation. */
  hour: number;
  /** Not now: never explained again on this phone; Home's line offers Turn on from now on. */
  later: () => void;
};

/** What the shell needs to explain reminders and say when they are off (RemindersViewModel). */
export function useReminders(): Reminders {
  const me = useHouseholdSession();
  const { pantry, preferences, clock } = useServices();
  const explained = usePreferences().notificationExplainerShown;
  const prefs = useNotificationPrefs();
  const permission = usePushStatus();
  const today = useToday(clock);
  // The pantry is listened to only while the explanation may still be due: not yet answered, the system can still ask
  // (not in Safari outside the Home Screen) and the member wants reminders. It is the household's shared listener.
  const mayAsk = !explained && permission === 'default' && prefs?.enabled === true;
  const dated = useObservable<Dated>(
    useMemo(
      () =>
        !mayAsk
          ? of(NOT_LISTENING)
          : pantry.observe(me.householdId).pipe(
              retryLoading<{ batches: StockBatch[]; fromCache: boolean }>(),
              scan<{ batches: StockBatch[]; fromCache: boolean }, Dated>(
                (acc, data) => ({ atOpen: acc.atOpen ?? (data.fromCache ? null : data.batches), now: data.batches }),
                NOT_LISTENING,
              ),
            ),
      [pantry, me.householdId, mayAsk],
    ),
    NOT_LISTENING,
  );
  const access: ReminderAccess = { permission, remindersOn: prefs?.enabled ?? null, explained };
  const markExplained = !explained && NotificationPrompt.explainedWithoutAsking(access);
  useEffect(() => {
    if (markExplained) preferences.set({ notificationExplainerShown: true });
  }, [markExplained, preferences]);
  return {
    ask: NotificationPrompt.shouldAsk(
      access,
      dated.atOpen === null ? null : NotificationPrompt.datedFood(dated.atOpen, today),
      NotificationPrompt.datedFood(dated.now, today),
    ),
    offOnThisPhone: NotificationPrompt.offOnThisPhone(access),
    hour: prefs ? NotificationRules.hour(prefs) : NotificationRules.DEFAULT_MINUTES / 60,
    later: useCallback(() => preferences.set({ notificationExplainerShown: true }), [preferences]),
  };
}
