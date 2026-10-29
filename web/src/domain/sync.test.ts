import { describe, expect, it } from 'vitest';
import { CURSOR_MARGIN_MS, cursor, FULL_SYNC_EVERY_MS } from './sync';

const now = Date.parse('2026-10-12T12:00:00Z');

describe('cursor', () => {
  it('reads everything without a cached change or a full read on record', () => {
    expect(cursor(null, now - 1000, now)).toBeNull();
    expect(cursor(now - 1000, null, now)).toBeNull();
  });

  it('reads only changes after the newest cached one, less the margin', () => {
    expect(cursor(now - 60_000, now - 3_600_000, now)).toBe(now - 60_000 - CURSOR_MARGIN_MS);
  });

  it('reads everything again once a day, or when the clock moved back', () => {
    expect(cursor(now - 60_000, now - FULL_SYNC_EVERY_MS, now)).toBeNull();
    expect(cursor(now - 60_000, now + 60_000, now)).toBeNull();
  });
});
