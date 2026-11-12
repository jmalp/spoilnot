// DESIGN 6.7's "Couldn't load. Retry" for one screen (after v1, review WEB-10; core/coroutines/LoadRetry.kt in the
// app). Its listeners go through retryLoading: one that fails listens again after 1, 2, 4 … 32 s, or at once when the
// person taps Retry, instead of ending or spinning; failed$ is true while any of them is failing.
import {
  BehaviorSubject,
  distinctUntilChanged,
  map,
  Observable,
  race,
  Subject,
  take,
  timer,
  type MonoTypeOperatorFunction,
  type Subscription,
} from 'rxjs';
import { isAppError } from '../core/errors';

/** How long a listener must stay up after a failure before "Couldn't load" goes. */
export const RECOVERED_AFTER_MS = 2_000;

/** The quota and Offline banners already say why: no "Couldn't load" for these (DESIGN 6.7). */
const explainedElsewhere = (error: unknown) => isAppError(error, 'quotaExceeded') || isAppError(error, 'offline');

/** 1, 2, 4 … 32 s, as the session and the changes-only listeners (DESIGN 7.5). */
export const loadRetryDelayMs = (attempt: number): number => 1000 * 2 ** Math.min(Math.max(attempt, 0), 5);

export class LoadRetry {
  private readonly failing = new BehaviorSubject<ReadonlySet<object>>(new Set());
  /** Retry taps. */
  readonly now$ = new Subject<void>();

  /** True while one of this screen's listeners has failed and hasn't brought anything since. */
  readonly failed$: Observable<boolean> = this.failing.pipe(
    map((s) => s.size > 0),
    distinctUntilChanged(),
  );

  /** Retry: listens again at once instead of at the end of the wait. */
  retry() {
    this.now$.next();
  }

  fail(token: object) {
    if (!this.failing.value.has(token)) this.failing.next(new Set([...this.failing.value, token]));
  }

  recover(token: object) {
    if (!this.failing.value.has(token)) return;
    const next = new Set(this.failing.value);
    next.delete(token);
    this.failing.next(next);
  }
}

/**
 * Listens again after a failure (DESIGN 6.7) instead of ending. With [load] the screen says "Couldn't load. Retry"
 * meanwhile and Retry listens again at once; without it the retry is silent (a secondary list, such as the members
 * who name an author). [fallback], when given, is emitted once when the very first listen fails, so screens that
 * combine it still show the rest; after the first value the last one stays while it retries. The message goes once a
 * listen has stayed up for RECOVERED_AFTER_MS.
 */
export function retryLoading<T>(load?: LoadRetry, ...fallback: [T] | []): MonoTypeOperatorFunction<T> {
  return (source) =>
    new Observable<T>((subscriber) => {
      const token = {};
      let emitted = false;
      let attempt = 0;
      let inner: Subscription | undefined;
      let wait: Subscription | undefined;
      let recovering: ReturnType<typeof setTimeout> | undefined;
      const stopRecovering = () => {
        if (recovering !== undefined) clearTimeout(recovering);
        recovering = undefined;
      };
      const listen = () => {
        inner = source.subscribe({
          next: (value) => {
            emitted = true;
            subscriber.next(value);
            // A listen that comes back shows the phone's copy first, then may fail again at once: the message goes
            // only once it has stayed up a moment, so it doesn't flicker on every retry.
            recovering ??= setTimeout(() => {
              recovering = undefined;
              attempt = 0;
              load?.recover(token);
            }, RECOVERED_AFTER_MS);
          },
          error: (error: unknown) => {
            stopRecovering();
            if (import.meta.env.DEV) console.warn('A listener failed; listening again', error);
            // The quota and the network have their own banners (DESIGN 6.7); they still retry.
            if (!explainedElsewhere(error)) load?.fail(token);
            if (!emitted && fallback.length > 0) {
              emitted = true;
              subscriber.next(fallback[0] as T);
            }
            const delay = timer(loadRetryDelayMs(attempt++));
            wait = (load ? race(delay, load.now$) : delay).pipe(take(1)).subscribe(() => listen());
          },
          complete: () => {
            stopRecovering();
            load?.recover(token);
            subscriber.complete();
          },
        });
      };
      listen();
      return () => {
        stopRecovering();
        inner?.unsubscribe();
        wait?.unsubscribe();
        load?.recover(token);
      };
    });
}
