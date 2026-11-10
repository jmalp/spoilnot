// One upstream per key, shared by every screen (SharedByKey in the app's core/coroutines; after v1, DESIGN 9.1).
import {
  asyncScheduler,
  defer,
  NEVER,
  race,
  ReplaySubject,
  share,
  take,
  timer,
  type Observable,
  type SchedulerLike,
} from 'rxjs';

/** DESIGN 9.1: a listener stays 5 s after its last screen leaves. */
export const STOP_TIMEOUT_MS = 5_000;

/**
 * One upstream per key, shared by everything that subscribes to it: the listeners start with the first subscriber
 * and stop [stopTimeoutMs] after the last one leaves, so a screen that is left (or a page that leaves the screen,
 * core/hooks) lets go of them, and a screen that comes back within that time finds them still running. The work of
 * the upstream (mapping snapshots) runs once, whoever subscribes.
 *
 * A subscriber that arrives later gets the latest value at once; nothing is kept after the upstream stops, so a value
 * from an old listener never stands in for the new one. A failure reaches every subscriber (each decides: retry, or
 * an empty screen), and the next subscription starts a fresh upstream. [scheduler] is the clock of the stop timer.
 *
 * [stopAtOnce$] emits, at subscription included, while the upstream should stop without waiting: on the web, a page
 * that left the screen, since iOS may suspend a Home Screen app before the timer fires, and resume its listeners
 * (billed in full after 30 minutes away) before the late timer stops them.
 */
export class SharedByKey<K, T> {
  private readonly shared = new Map<K, Observable<T>>();

  constructor(
    private readonly stopTimeoutMs: number = STOP_TIMEOUT_MS,
    private readonly stopAtOnce$: Observable<unknown> = NEVER,
    private readonly scheduler: SchedulerLike = asyncScheduler,
  ) {}

  /** The shared stream for [key]; [upstream] is called only when none for it is running. */
  get(key: K, upstream: () => Observable<T>): Observable<T> {
    return defer(() => {
      let entry = this.shared.get(key);
      if (!entry) {
        entry = this.share(upstream);
        this.shared.set(key, entry);
      }
      return entry;
    });
  }

  private share(upstream: () => Observable<T>): Observable<T> {
    return defer(upstream).pipe(
      share({
        // A new subject per run: the latest value is replayed only while the upstream runs.
        connector: () => new ReplaySubject<T>(1),
        resetOnError: true,
        resetOnComplete: true,
        resetOnRefCountZero:
          this.stopTimeoutMs > 0
            ? () => race(timer(this.stopTimeoutMs, this.scheduler), this.stopAtOnce$.pipe(take(1)))
            : true,
      }),
    );
  }
}
