// DESIGN 6.7's "Couldn't load. Retry" (LoadRetryTest in the app): a failed listener listens again instead of ending.
import { Observable } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppError } from '../core/errors';
import { LoadRetry, loadRetryDelayMs, retryLoading } from './load';

/** A listener that fails its first [failures] listens, then emits 'ok'. */
function listener(failures: number, listens: { count: number }): Observable<string> {
  return new Observable<string>((subscriber) => {
    listens.count++;
    if (listens.count <= failures) subscriber.error(new Error('refused'));
    else subscriber.next('ok');
  });
}

describe('retryLoading', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('shows the error, listens again after 1, 2 and 4 s, and clears when data comes', () => {
    const load = new LoadRetry();
    const listens = { count: 0 };
    const values: string[] = [];
    const failed: boolean[] = [];
    load.failed$.subscribe((f) => failed.push(f));
    const subscription = listener(3, listens)
      .pipe(retryLoading(load))
      .subscribe((v) => values.push(v));
    expect([listens.count, failed]).toEqual([1, [false, true]]);
    vi.advanceTimersByTime(1_000);
    expect(listens.count).toBe(2);
    vi.advanceTimersByTime(2_000);
    expect(listens.count).toBe(3);
    vi.advanceTimersByTime(4_000);
    expect([listens.count, values, failed]).toEqual([4, ['ok'], [false, true]]);
    // The message goes once the listen has stayed up for 2 s.
    vi.advanceTimersByTime(2_000);
    expect(failed).toEqual([false, true, false]);
    subscription.unsubscribe();
  });

  it('keeps the message without flickering while each listen shows the cache and fails again', () => {
    const load = new LoadRetry();
    const failed: boolean[] = [];
    load.failed$.subscribe((f) => failed.push(f));
    // As a refused listener does: the phone's copy first, then the refusal.
    const refused = new Observable<string>((subscriber) => {
      subscriber.next('cached');
      const t = setTimeout(() => subscriber.error(new Error('refused')), 10);
      return () => clearTimeout(t);
    });
    const subscription = refused.pipe(retryLoading(load)).subscribe();
    vi.advanceTimersByTime(30_000);
    expect(failed).toEqual([false, true]);
    subscription.unsubscribe();
  });

  it('listens again at once on Retry', () => {
    const load = new LoadRetry();
    const listens = { count: 0 };
    const values: string[] = [];
    const subscription = listener(5, listens)
      .pipe(retryLoading(load))
      .subscribe((v) => values.push(v));
    for (let i = 0; i < 5; i++) load.retry();
    expect([listens.count, values]).toEqual([6, ['ok']]);
    subscription.unsubscribe();
  });

  it('emits a fallback once when the first listen fails', () => {
    const listens = { count: 0 };
    const values: string[] = [];
    const subscription = listener(2, listens)
      .pipe(retryLoading(undefined, 'none'))
      .subscribe((v) => values.push(v));
    vi.advanceTimersByTime(10_000);
    expect(values).toEqual(['none', 'ok']);
    subscription.unsubscribe();
  });

  it('stops listening and clears the error when the screen leaves', () => {
    const load = new LoadRetry();
    const failed: boolean[] = [];
    load.failed$.subscribe((f) => failed.push(f));
    const listens = { count: 0 };
    const subscription = listener(99, listens).pipe(retryLoading(load)).subscribe();
    subscription.unsubscribe();
    vi.advanceTimersByTime(60_000);
    expect([listens.count, failed]).toEqual([1, [false, true, false]]);
  });

  it("doesn't say Couldn't load for the quota or the network, which have their own banners, but retries", () => {
    const load = new LoadRetry();
    const failed: boolean[] = [];
    load.failed$.subscribe((f) => failed.push(f));
    let listens = 0;
    const quota = new Observable<string>((subscriber) => {
      listens++;
      subscriber.error(new AppError(listens % 2 === 0 ? 'offline' : 'quotaExceeded'));
    });
    const subscription = quota.pipe(retryLoading(load)).subscribe();
    vi.advanceTimersByTime(4_000);
    expect([listens, failed]).toEqual([3, [false]]);
    subscription.unsubscribe();
  });

  it('waits 1, 2, 4 … 32 s and no longer', () => {
    expect([0, 1, 2, 3, 4, 5, 6].map(loadRetryDelayMs)).toEqual([1, 2, 4, 8, 16, 32, 32].map((s) => s * 1000));
  });
});
