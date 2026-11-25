// Ported from SharedByKeyTest: one upstream per key, kept STOP_TIMEOUT_MS after the last subscriber, nothing replayed
// after it stops, and failures passed to every subscriber.
import { BehaviorSubject, defer, filter, finalize, firstValueFrom, Subject, type Observable } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SharedByKey, STOP_TIMEOUT_MS } from './shared';

/** An upstream that counts how often it was started and is running. */
class Upstream<T> {
  starts = 0;
  running = 0;
  readonly values: Subject<T>;
  constructor(initial?: T) {
    this.values = initial === undefined ? new Subject<T>() : new BehaviorSubject<T>(initial);
  }
  readonly make = (): Observable<T> =>
    defer(() => {
      this.starts++;
      this.running++;
      return this.values.pipe(finalize(() => this.running--));
    });
}

describe('SharedByKey', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('every subscriber of a key shares one upstream; another key gets its own', () => {
    const shared = new SharedByKey<string, number>();
    const a = new Upstream(1);
    const b = new Upstream(2);
    const seen: number[] = [];
    const subs = [
      shared.get('h1', a.make).subscribe((v) => seen.push(v)),
      shared.get('h1', a.make).subscribe((v) => seen.push(v)),
      shared.get('h1', a.make).subscribe((v) => seen.push(v)),
      shared.get('h2', b.make).subscribe((v) => seen.push(v)),
    ];
    expect(a.starts).toBe(1);
    expect(b.starts).toBe(1);
    expect(seen).toEqual([1, 1, 1, 2]);
    a.values.next(3);
    expect(seen).toEqual([1, 1, 1, 2, 3, 3, 3]);
    subs.forEach((s) => s.unsubscribe());
  });

  it('a subscriber that joins later gets the latest value at once', () => {
    const shared = new SharedByKey<string, number>();
    const up = new Upstream<number>();
    const first = shared.get('h', up.make).subscribe();
    up.values.next(7);
    const seen: number[] = [];
    const second = shared.get('h', up.make).subscribe((v) => seen.push(v));
    expect(seen).toEqual([7]);
    expect(up.starts).toBe(1);
    first.unsubscribe();
    second.unsubscribe();
  });

  it('stops STOP_TIMEOUT_MS after the last subscriber leaves, and a return before that keeps it', async () => {
    const shared = new SharedByKey<string, number>();
    const up = new Upstream(1);
    shared.get('h', up.make).subscribe().unsubscribe();
    await vi.advanceTimersByTimeAsync(STOP_TIMEOUT_MS - 1);
    expect(up.running).toBe(1);
    // Back within the time (another tab of the same household): the same upstream.
    const back = shared.get('h', up.make).subscribe();
    await vi.advanceTimersByTimeAsync(STOP_TIMEOUT_MS * 2);
    expect(up.starts).toBe(1);
    expect(up.running).toBe(1);
    back.unsubscribe();
    await vi.advanceTimersByTimeAsync(STOP_TIMEOUT_MS);
    expect(up.running).toBe(0);
  });

  it('replays nothing from a stopped upstream: the next subscriber waits for the new one', async () => {
    const shared = new SharedByKey<string, number>();
    const up = new Upstream<number>();
    const sub = shared.get('h', up.make).subscribe();
    up.values.next(1);
    sub.unsubscribe();
    await vi.advanceTimersByTimeAsync(STOP_TIMEOUT_MS);
    expect(up.running).toBe(0);
    const seen: number[] = [];
    const again = shared.get('h', up.make).subscribe((v) => seen.push(v));
    expect(up.starts).toBe(2);
    expect(seen).toEqual([]);
    up.values.next(2);
    expect(seen).toEqual([2]);
    again.unsubscribe();
  });

  it('a failure reaches every subscriber, and the next subscription listens again', async () => {
    const shared = new SharedByKey<string, number>();
    let starts = 0;
    const listener = new Subject<number>();
    // The first listener fails; the next one works.
    const upstream = () =>
      defer(() => {
        starts++;
        return starts === 1 ? listener : new BehaviorSubject(5);
      });
    const errors: unknown[] = [];
    shared.get('h', upstream).subscribe({ error: (e) => errors.push(e) });
    shared.get('h', upstream).subscribe({ error: (e) => errors.push(e) });
    listener.error(new Error('quota'));
    expect(errors).toHaveLength(2);
    expect(starts).toBe(1);
    expect(await firstValueFrom(shared.get('h', upstream))).toBe(5);
    expect(starts).toBe(2);
  });

  it('with no stop time, the upstream stops as the last subscriber leaves (the browser tests)', () => {
    const shared = new SharedByKey<string, number>(0);
    const up = new Upstream(1);
    shared.get('h', up.make).subscribe().unsubscribe();
    expect(up.running).toBe(0);
  });

  it('stops at once while the page is hidden, and keeps the stop time while it is shown', async () => {
    const hidden = new BehaviorSubject(false);
    const shared = new SharedByKey<string, number>(STOP_TIMEOUT_MS, hidden.pipe(filter((h) => h)));
    const up = new Upstream(1);
    // Moving between tabs: the stop time applies.
    shared.get('h', up.make).subscribe().unsubscribe();
    expect(up.running).toBe(1);
    const tab = shared.get('h', up.make).subscribe();
    // The page leaves the screen and its screens let go (core/hooks): no timer to wait for.
    hidden.next(true);
    tab.unsubscribe();
    expect(up.running).toBe(0);
    // Hidden while the stop time runs: it stops then too.
    hidden.next(false);
    shared.get('h', up.make).subscribe().unsubscribe();
    expect(up.running).toBe(1);
    hidden.next(true);
    expect(up.running).toBe(0);
    expect(up.starts).toBe(2);
  });
});
