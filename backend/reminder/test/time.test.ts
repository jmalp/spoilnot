import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { addDays, daysBetween, toLocal, zonedInstant } from '../src/time.ts';

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

describe('zonedInstant (after v1, B25)', () => {
  const at = (date: string, minutes: number, zone: string | null) => zonedInstant(date, minutes, zone).toISOString();

  it('is the moment the local clock reads that date and time', () => {
    assert.equal(at('2026-10-12', 9 * 60, 'America/New_York'), '2026-10-12T13:00:00.000Z');
    assert.equal(at('2026-10-12', 9 * 60, 'Asia/Tokyo'), '2026-10-12T00:00:00.000Z');
    assert.equal(at('2026-10-12', 5 * 60, 'Asia/Kolkata'), '2026-10-11T23:30:00.000Z');
    assert.equal(at('2026-10-12', 9 * 60, 'Asia/Kathmandu'), '2026-10-12T03:15:00.000Z');
    assert.equal(at('2026-10-12', 21 * 60, 'Pacific/Kiritimati'), '2026-10-12T07:00:00.000Z');
    assert.equal(at('2026-10-12', 0, 'Pacific/Pago_Pago'), '2026-10-12T11:00:00.000Z');
    assert.equal(at('2026-12-31', 21 * 60, 'UTC'), '2026-12-31T21:00:00.000Z');
  });

  it('falls back to UTC like toLocal', () => {
    assert.equal(at('2026-10-12', 9 * 60, null), '2026-10-12T09:00:00.000Z');
    assert.equal(at('2026-10-12', 9 * 60, 'Not/AZone'), '2026-10-12T09:00:00.000Z');
  });

  it('follows the daylight saving days on both sides of the change', () => {
    assert.equal(at('2026-03-08', 9 * 60, 'America/New_York'), '2026-03-08T13:00:00.000Z');
    assert.equal(at('2026-03-07', 9 * 60, 'America/New_York'), '2026-03-07T14:00:00.000Z');
    assert.equal(at('2026-11-01', 9 * 60, 'America/New_York'), '2026-11-01T14:00:00.000Z');
    assert.equal(at('2026-10-25', 9 * 60, 'Europe/Madrid'), '2026-10-25T08:00:00.000Z');
    assert.equal(at('2026-03-29', 9 * 60, 'Europe/Madrid'), '2026-03-29T07:00:00.000Z');
    // Lord Howe Island moves its clocks by half an hour.
    assert.equal(at('2026-10-04', 9 * 60, 'Australia/Lord_Howe'), '2026-10-03T22:00:00.000Z');
  });

  it('a time the clocks skip is the moment they skip to; a time they repeat is its first time', () => {
    // 2026-03-08 in New York goes from 01:59 EST to 03:00 EDT (07:00 UTC).
    assert.equal(at('2026-03-08', 2 * 60 + 30, 'America/New_York'), '2026-03-08T07:00:00.000Z');
    assert.equal(at('2026-03-08', 3 * 60, 'America/New_York'), '2026-03-08T07:00:00.000Z');
    // 2026-11-01 goes from 01:59 EDT back to 01:00 EST: 01:30 happens at 05:30 and again at 06:30 UTC.
    assert.equal(at('2026-11-01', 90, 'America/New_York'), '2026-11-01T05:30:00.000Z');
    // Santiago skips from midnight to 01:00 (2026-09-06).
    assert.equal(at('2026-09-06', 0, 'America/Santiago'), '2026-09-06T04:00:00.000Z');
  });

  it('agrees with toLocal for every minute the apps can store, in several zones', () => {
    for (const zone of ['America/New_York', 'Europe/Madrid', 'Asia/Kolkata', 'Australia/Lord_Howe', 'UTC']) {
      for (const date of ['2026-03-08', '2026-03-29', '2026-10-04', '2026-10-25', '2026-11-01']) {
        for (let minutes = 0; minutes < 1440; minutes += 7) {
          const t = zonedInstant(date, minutes, zone);
          const local = toLocal(t, zone);
          const reached = local.date === date && local.minutesOfDay >= minutes;
          assert.ok(reached, `${zone} ${date} ${minutes}: ${t.toISOString()}`);
          // A minute earlier the clock had not got there yet.
          const before = toLocal(new Date(t.getTime() - 60_000), zone);
          assert.ok(before.date < date || (before.date === date && before.minutesOfDay < minutes), `${zone} ${date} ${minutes}`);
        }
      }
    }
  });
});
