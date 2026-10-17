import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { addDays, daysBetween, toLocal } from '../src/time.ts';

describe('toLocal', () => {
  it('converts on both sides of UTC', () => {
    const now = new Date('2026-10-12T23:30:00Z');
    assert.deepEqual(toLocal(now, 'UTC'), { date: '2026-10-12', minutesOfDay: 23 * 60 + 30 });
    assert.deepEqual(toLocal(now, 'Asia/Tokyo'), { date: '2026-10-13', minutesOfDay: 8 * 60 + 30 });
    assert.deepEqual(toLocal(now, 'America/Los_Angeles'), { date: '2026-10-12', minutesOfDay: 16 * 60 + 30 });
    assert.deepEqual(toLocal(now, 'Asia/Kolkata'), { date: '2026-10-13', minutesOfDay: 5 * 60 });
    assert.deepEqual(toLocal(now, 'Pacific/Kiritimati'), { date: '2026-10-13', minutesOfDay: 13 * 60 + 30 });
  });

  it('follows US daylight saving: start on 2026-03-08, end on 2026-11-01', () => {
    // 9:30 AM New York is 14:30 UTC in winter (UTC-5) and 13:30 UTC in summer (UTC-4).
    assert.equal(toLocal(new Date('2026-03-07T14:30:00Z'), 'America/New_York').minutesOfDay, 9 * 60 + 30);
    assert.equal(toLocal(new Date('2026-03-08T13:30:00Z'), 'America/New_York').minutesOfDay, 9 * 60 + 30);
    assert.equal(toLocal(new Date('2026-11-01T14:30:00Z'), 'America/New_York').minutesOfDay, 9 * 60 + 30);
    // The skipped hour: 06:59 UTC is 01:59 EST, a minute later it is 03:00 EDT.
    assert.equal(toLocal(new Date('2026-03-08T06:59:00Z'), 'America/New_York').minutesOfDay, 1 * 60 + 59);
    assert.equal(toLocal(new Date('2026-03-08T07:00:00Z'), 'America/New_York').minutesOfDay, 3 * 60);
  });

  it('follows EU daylight saving: end on 2026-10-25', () => {
    assert.equal(toLocal(new Date('2026-10-24T07:00:00Z'), 'Europe/Madrid').minutesOfDay, 9 * 60);
    assert.equal(toLocal(new Date('2026-10-25T08:00:00Z'), 'Europe/Madrid').minutesOfDay, 9 * 60);
  });

  it('falls back to UTC for a missing or unknown zone', () => {
    const now = new Date('2026-10-12T10:00:00Z');
    assert.deepEqual(toLocal(now, null), { date: '2026-10-12', minutesOfDay: 600 });
    assert.deepEqual(toLocal(now, 'Not/AZone'), { date: '2026-10-12', minutesOfDay: 600 });
  });
});

describe('calendar arithmetic', () => {
  it('counts whole days across month, year and leap-day boundaries', () => {
    assert.equal(daysBetween('2026-10-31', '2026-11-01'), 1);
    assert.equal(daysBetween('2026-12-31', '2027-01-01'), 1);
    assert.equal(daysBetween('2028-02-28', '2028-03-01'), 2);
    assert.equal(daysBetween('2026-10-12', '2026-10-09'), -3);
    // DST does not change a calendar day count.
    assert.equal(daysBetween('2026-11-01', '2026-11-02'), 1);
  });

  it('adds days', () => {
    assert.equal(addDays('2026-12-30', 3), '2027-01-02');
    assert.equal(addDays('2026-03-01', -1), '2026-02-28');
  });
});
