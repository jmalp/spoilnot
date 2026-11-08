// The job's pure parts (DESIGN 10.3, 10.4): the schedule keeps one digest a day per user, the daily prune key, which
// FCM errors delete a token, and sends in chunks. The whole job runs against the emulator in test/integration.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { describe, it } from 'node:test';
import { afterSend, DEFAULT_PREFS, dueAt, type UserDoc } from '../src/digest.ts';
import { chunks, DEAD_TOKEN_CODES, isDeadToken, pruneDue, SEND_CHUNK, utcDate } from '../src/job.ts';

/** The minutes of the workflow's schedule entries ('7 * * * *' and so on). */
function scheduledMinutes(): number[] {
  const yml = readFileSync(new URL('../../../.github/workflows/reminders.yml', import.meta.url), 'utf8');
  return [...yml.matchAll(/cron: '(\d+) \* \* \* \*'/g)].map((m) => Number(m[1])).sort((a, b) => a - b);
}

/** Every scheduled start from [from] for [days] days, keeping each one [keep] says to (GitHub drops many). */
function starts(from: string, days: number, keep: (i: number) => boolean = () => true): Date[] {
  const minutes = scheduledMinutes();
  const out: Date[] = [];
  const start = new Date(`${from}T00:00:00Z`).getTime();
  for (let h = 0; h < days * 24; h++) {
    for (const m of minutes) out.push(new Date(start + h * 3_600_000 + m * 60_000));
  }
  return out.filter((_, i) => keep(i));
}

const user = (uid: string, timezone: string, hour: number): UserDoc => ({
  uid, householdId: 'h1', timezone, locale: 'en', prefs: { ...DEFAULT_PREFS, digestMinutes: hour * 60 },
  lastDigestDate: null, lastExpiredThrough: null,
});

/** Replays [runs] through the job's own rules: who is due, then what a delivered digest records. */
function replay(users: UserDoc[], runs: Date[]): Map<string, Array<{ date: string; late: number }>> {
  const sent = new Map<string, Array<{ date: string; late: number }>>(users.map((u) => [u.uid, []]));
  for (const now of runs) {
    for (const u of users) {
      const local = dueAt(u, now);
      if (!local) continue;
      sent.get(u.uid)!.push({ date: local.date, late: local.minutesOfDay - u.prefs.digestMinutes });
      Object.assign(u, afterSend(local.date));
    }
  }
  return sent;
}

// Every hour the apps offer (5 to 21), on both sides of UTC, over the US daylight saving change (1 November 2026).
const ZONES = ['America/New_York', 'Europe/Madrid', 'Asia/Tokyo', 'America/Los_Angeles', 'UTC'];
const everyone = () => ZONES.flatMap((zone) =>
  Array.from({ length: 17 }, (_, i) => user(`${zone}-${i + 5}`, zone, i + 5)));

describe('the schedule (DESIGN 10.4)', () => {
  it('has three entries an hour, off the top of the hour', () => {
    assert.deepEqual(scheduledMinutes(), [7, 27, 47]);
  });

  it('sends each user exactly one digest a local day, within 20 minutes of the chosen hour', () => {
    const users = everyone();
    const sent = replay(users, starts('2026-10-30', 6));
    for (const u of users) {
      const days = sent.get(u.uid)!;
      assert.equal(new Set(days.map((d) => d.date)).size, days.length, `${u.uid}: never twice a day`);
      // The whole local days inside the replay.
      const whole = days.filter((d) => d.date >= '2026-10-31' && d.date <= '2026-11-03');
      assert.equal(whole.length, 4, `${u.uid}: one each day`);
      assert.ok(whole.every((d) => d.late >= 0 && d.late < 20), `${u.uid}: on time`);
    }
  });

  it('when GitHub drops most runs, still at most one a day', () => {
    // Keep about one start in five, at a fixed pseudo-random pattern.
    const users = everyone();
    const sent = replay(users, starts('2026-10-30', 5, (i) => (i * 7919) % 5 === 0));
    for (const u of users) {
      const dates = sent.get(u.uid)!.map((d) => d.date);
      assert.equal(new Set(dates).size, dates.length, u.uid);
    }
  });
});

describe('the daily prune key', () => {
  it('prunes in the first run of each UTC day', () => {
    assert.equal(pruneDue(null, new Date('2026-11-08T00:07:00Z')), true, 'never pruned');
    assert.equal(pruneDue('2026-11-07', new Date('2026-11-08T00:07:00Z')), true);
    assert.equal(pruneDue('2026-11-08', new Date('2026-11-08T23:47:00Z')), false, 'once a day');
    assert.equal(utcDate(new Date('2026-11-08T23:59:59Z')), '2026-11-08');
  });

  it('over a day of scheduled runs, only the first prunes when each prune succeeds', () => {
    let last: string | null = '2026-11-07';
    const pruned: Date[] = [];
    for (const now of starts('2026-11-08', 1)) {
      if (pruneDue(last, now)) {
        pruned.push(now);
        last = utcDate(now);
      }
    }
    assert.deepEqual(pruned, [new Date('2026-11-08T00:07:00Z')]);
  });
});

describe('sending', () => {
  it('deletes a token only when FCM says it is unregistered or invalid', () => {
    assert.equal(isDeadToken('messaging/registration-token-not-registered'), true);
    assert.equal(isDeadToken('messaging/invalid-registration-token'), true);
    // Also what FCM answers for a message it refuses, so the token stays.
    assert.equal(isDeadToken('messaging/invalid-argument'), false);
    assert.equal(isDeadToken('messaging/internal-error'), false);
    assert.equal(isDeadToken(undefined), false);
    assert.equal(DEAD_TOKEN_CODES.size, 2);
  });

  it('sends in chunks of 500', () => {
    assert.equal(SEND_CHUNK, 500);
    assert.deepEqual(chunks([1, 2, 3, 4, 5], 2), [[1, 2], [3, 4], [5]]);
    assert.deepEqual(chunks(Array.from({ length: 1001 }, (_, i) => i), SEND_CHUNK).map((c) => c.length), [500, 500, 1]);
    assert.deepEqual(chunks([], 500), []);
  });
});
