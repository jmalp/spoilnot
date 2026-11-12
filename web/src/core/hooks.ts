import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { EMPTY, retry, switchMap, timer, type Observable } from 'rxjs';
import { LoadRetry } from '../domain/load';
import { today as todayOf, type Clock, type LocalDate } from '../domain/time';
import { messageId } from './errors';
import type { StringId } from './i18n';
import { pageVisible$ } from './visibility';

/** How long a screen waits before listening again after its data failed to load. */
export const RELISTEN_MS = 3_000;

/**
 * The latest value of [source] (collectAsStateWithLifecycle). [source] must be stable across renders (useMemo).
 * Errors keep the last value, like `.catch {}` in the app's view models, and the screen listens again
 * [RELISTEN_MS] later: a listener refused right after creating or joining a household, while the server is slow to
 * confirm the membership, would otherwise leave the screen loading until it is reopened. Screens close when the
 * session changes, so a real refusal (removed from the household) does not retry for long.
 *
 * Like the app's lifecycle, it listens only while the page is on screen (after v1, DESIGN 9.1): when the page is
 * hidden it lets go of [source], keeping the last value, and listens again when the page comes back. A Home Screen
 * app that iOS suspended would otherwise resume every listener of the screen it was on, billed in full after 30
 * minutes away (EFF-01).
 */
export function useObservable<T>(source: Observable<T>, initial: T): T {
  const [value, setValue] = useState<T>(initial);
  useEffect(() => {
    const subscription = pageVisible$
      .pipe(switchMap((visible) => (visible ? source.pipe(retry({ delay: () => timer(RELISTEN_MS) })) : EMPTY)))
      .subscribe({ next: setValue });
    return () => subscription.unsubscribe();
  }, [source]);
  return value;
}

/**
 * DESIGN 6.7's "Couldn't load. Retry" for one screen (domain/load): pipe the screen's listeners through
 * `retryLoading(load)`; `failed` is true while one of them is failing, and `load.retry()` listens again at once.
 */
export function useLoadRetry(): { load: LoadRetry; failed: boolean } {
  const load = useMemo(() => new LoadRetry(), []);
  const failed = useObservable(load.failed$, false);
  return { load, failed };
}

/**
 * One action at a time with its busy flag and error message (the `launch` helper of the app's view models).
 * [run] ignores a call while another is running.
 */
export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<StringId | null>(null);
  const running = useRef(false);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  const run = useCallback(async (work: () => Promise<void>): Promise<boolean> => {
    if (running.current) return false;
    running.current = true;
    setBusy(true);
    setError(null);
    try {
      await work();
      return true;
    } catch (e) {
      if (mounted.current) setError(messageId(e));
      return false;
    } finally {
      running.current = false;
      if (mounted.current) setBusy(false);
    }
  }, []);
  const clearError = useCallback(() => setError(null), []);
  return { busy, error, run, clearError };
}

/** "Today" in the phone's calendar, re-checked every minute so statuses roll over at midnight (todayFlow). */
export function useToday(clock: Clock): LocalDate {
  const [day, setDay] = useState(() => todayOf(clock));
  useEffect(() => {
    const timer = window.setInterval(() => setDay(todayOf(clock)), 60_000);
    return () => window.clearInterval(timer);
  }, [clock]);
  return day;
}

/** The clock's time, refreshed every minute: for "5 minutes ago" lines. */
export function useNow(clock: Clock): number {
  const [now, setNow] = useState(() => clock.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(clock.now()), 60_000);
    return () => window.clearInterval(timer);
  }, [clock]);
  return now;
}
