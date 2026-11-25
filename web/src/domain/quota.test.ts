// Ported from QuotaSignalTest.
import { afterEach, describe, expect, it, vi } from 'vitest';
import { QuotaSignal, quotaDay } from './quota';

describe('QuotaSignal', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('the quota day is the date in Los Angeles', () => {
    // 07:30 UTC on Oct 18 is still Oct 18 00:30 in Los Angeles (PDT, UTC-7); 06:59 UTC is Oct 17.
    expect(quotaDay(Date.parse('2026-10-18T07:30:00Z'))).toBe('2026-10-18');
    expect(quotaDay(Date.parse('2026-10-18T06:59:00Z'))).toBe('2026-10-17');
    // Winter (PST, UTC-8).
    expect(quotaDay(Date.parse('2026-12-01T07:59:00Z'))).toBe('2026-11-30');
  });

  it('the quota banner lasts until midnight Pacific time', () => {
    let now = Date.parse('2026-10-18T07:30:00Z');
    const signal = new QuotaSignal({ now: () => now });
    expect(signal.isExhausted()).toBe(false);
    signal.report();
    now = Date.parse('2026-10-19T06:59:00Z');
    expect(signal.isExhausted()).toBe(true);
    expect(signal.untilResetMs()).toBe(60_000);
    now = Date.parse('2026-10-19T07:01:00Z');
    expect(signal.isExhausted()).toBe(false);
  });

  it('the banner turns itself off at midnight Pacific without a redraw', async () => {
    vi.useFakeTimers();
    // 23:58 in Los Angeles; the clock follows the fake timers.
    const start = Date.parse('2026-10-19T06:58:00Z');
    const t0 = Date.now();
    const signal = new QuotaSignal({ now: () => start + (Date.now() - t0) });
    const shown: boolean[] = [];
    const subscription = signal.banner$.subscribe((v) => shown.push(v));
    expect(shown).toEqual([false]);

    signal.report();
    expect(shown).toEqual([false, true]);
    expect(signal.untilResetMs()).toBe(120_000);
    await vi.advanceTimersByTimeAsync(119_000);
    expect(shown).toEqual([false, true]);
    await vi.advanceTimersByTimeAsync(2_000);
    expect(shown).toEqual([false, true, false]);
    subscription.unsubscribe();
  });
});
