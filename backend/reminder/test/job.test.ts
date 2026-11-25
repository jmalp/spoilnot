// The job's pure parts (DESIGN 10.3, 10.4): the schedule keeps one digest a day per user, the daily prune key, which
// FCM errors delete a token, and sends in chunks. The whole job runs against the emulator in test/integration.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { describe, it } from 'node:test';
import { Timestamp } from 'firebase-admin/firestore';
import { afterSend, DEFAULT_PREFS, dueAt, nextDigestAt, type UserDoc } from '../src/digest.ts';
import {
  chunks, CURSOR_MARGIN_MS, DEAD_TOKEN_CODES, isDeadToken, joinRequestPrunable, pruneDue, scheduleChanged, SEND_CHUNK,
  utcDate, waitingOn,
} from '../src/job.ts';

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

/** The on-time trigger's starts (every quarter hour, DESIGN 10.4) and GitHub's scheduled ones, in order. */
function everyStart(from: string, days: number): Date[] {
  const quarters = Array.from({ length: days * 96 }, (_, i) => new Date(Date.parse(`${from}T00:00:00Z`) + i * 15 * 60_000));
  return [...quarters, ...starts(from, days)].sort((a, b) => a.getTime() - b.getTime());
}

type Change = Partial<Omit<UserDoc, 'prefs'>> & { prefs?: Partial<UserDoc['prefs']> };

interface Replay {
  /** Today's selection: every user with reminders on is read and checked in every run. */
  before: string[];
  /** B25's: the users whose nextDigestAt has come (everyone on the day's first run) and those changed since. */
  after: string[];
  /** Profiles B25's selection read. */
  reads: number;
}

/**
 * Replays [runs] twice from the same profiles, with today's selection and with B25's, as runJob does it: a full read
 * in the first run of each UTC day, otherwise `nextDigestAt <= now`, plus `updatedAt` after the cursor; a due user
 * either gets their digest (or nothing to report, or gives up: all record the day) or is [skipped] (no devices, a
 * failed send), and every other user read gets a new nextDigestAt. [changes] are profile writes by the apps between
 * runs, which stamp updatedAt.
 */
function replayBoth(
  users: UserDoc[],
  runs: Date[],
  { changes = () => [], skipped = () => false }: {
    changes?: (run: number) => Array<[string, Change]>;
    skipped?: (uid: string, run: number) => boolean;
  } = {},
): Replay {
  const clone = () => users.map((u) => ({ ...u, prefs: { ...u.prefs } }));
  const before = clone();
  const after = clone().map((doc) => ({ doc, next: undefined as Timestamp | null | undefined, updatedAt: 0 }));
  const out: Replay = { before: [], after: [], reads: 0 };
  let cursor: number | null = null;
  let lastRead: string | null = null;
  runs.forEach((now, run) => {
    for (const [uid, change] of changes(run)) {
      const apply = (u: UserDoc) => Object.assign(u, { ...change, prefs: { ...u.prefs, ...change.prefs } });
      apply(before.find((u) => u.uid === uid)!);
      const p = after.find((x) => x.doc.uid === uid)!;
      apply(p.doc);
      p.updatedAt = now.getTime() - 60_000;
    }
    const line = (u: UserDoc, date: string) => `${u.uid} ${date} ${now.toISOString()}`;
    for (const u of before) {
      const local = dueAt(u, now);
      if (!local || skipped(u.uid, run)) continue;
      out.before.push(line(u, local.date));
      Object.assign(u, afterSend(local.date));
    }

    const full = cursor === null || pruneDue(lastRead, now);
    const read = after.filter((p) =>
      (full ? p.doc.prefs.enabled : p.next instanceof Timestamp && p.next.toMillis() <= now.getTime()) ||
      (cursor !== null && p.updatedAt > cursor));
    out.reads += read.length;
    for (const p of read) {
      const local = dueAt(p.doc, now);
      if (local && !skipped(p.doc.uid, run)) {
        out.after.push(line(p.doc, local.date));
        Object.assign(p.doc, afterSend(local.date));
        const next = nextDigestAt(p.doc, now);
        p.next = next && Timestamp.fromDate(next);
        continue;
      }
      const target = nextDigestAt(p.doc, now);
      if (scheduleChanged(p.next, target, now)) p.next = target && Timestamp.fromDate(target);
    }
    cursor = now.getTime() - CURSOR_MARGIN_MS;
    if (full) lastRead = utcDate(now);
  });
  return out;
}

// The daylight saving days of 2026: Europe ends 10-25, the US ends 11-01, the US starts 03-08, Europe starts 03-29.
const DST_WEEKS = [['2026-10-23', 4], ['2026-10-30', 4], ['2026-03-06', 4], ['2026-03-27', 4]] as const;

/** Odd times the rules allow (any minute 0 to 1439) and zones with half-hour offsets, besides the offered hours. */
const oddOnes = () => [
  ...[0, 59, 90, 150, 1319, 1320, 1439].map((m) => ({ ...user(`ny-odd-${m}`, 'America/New_York', 0), prefs: { ...DEFAULT_PREFS, digestMinutes: m } })),
  ...[90, 150].map((m) => ({ ...user(`madrid-odd-${m}`, 'Europe/Madrid', 0), prefs: { ...DEFAULT_PREFS, digestMinutes: m } })),
  user('kolkata-9', 'Asia/Kolkata', 9),
  user('kathmandu-7', 'Asia/Kathmandu', 7),
  user('no-zone-9', null as unknown as string, 9),
  user('bad-zone-9', 'Not/AZone', 9),
];

describe('reading only the users who are due (after v1, B25)', () => {
  for (const [from, days] of DST_WEEKS) {
    it(`sends exactly what reading everyone sends, every quarter hour from ${from} for ${days} days`, () => {
      const users = [...everyone(), ...oddOnes()];
      const { before, after, reads } = replayBoth(users, everyStart(from, days));
      assert.ok(before.length >= users.length * (days - 1), 'the replay sends digests');
      assert.deepEqual(after, before);
      // One full read a day plus each user's due read: about two reads a user a day, not one per run.
      assert.ok(reads <= users.length * days * 3, `${reads} reads`);
    });
  }

  it('the same with phones that miss runs, and with profiles changed between runs', () => {
    const users = [...everyone(), ...oddOnes()];
    const uids = users.map((u) => u.uid);
    // Each change at a fixed pseudo-random run: earlier and later hours, a move east or west, reminders off and on,
    // leaving a household and joining one.
    const edits: Change[] = [
      { prefs: { digestMinutes: 5 * 60 } }, { prefs: { digestMinutes: 21 * 60 } }, { timezone: 'Asia/Tokyo' },
      { timezone: 'America/Los_Angeles' }, { prefs: { enabled: false } }, { prefs: { enabled: true } },
      { householdId: null }, { householdId: 'h1' }, { timezone: 'Pacific/Kiritimati' }, { timezone: 'Pacific/Pago_Pago' },
    ];
    const changes = (run: number): Array<[string, Change]> =>
      run % 7 === 3 ? [[uids[(run * 31) % uids.length]!, edits[(run * 17) % edits.length]!]] : [];
    // A user with no device or whose send failed: about one run in three, at a fixed pattern.
    const skipped = (uid: string, run: number) => (uid.length * 7 + run * 13) % 3 === 0;
    for (const [from, days] of DST_WEEKS) {
      const { before, after } = replayBoth(users, everyStart(from, days), { changes, skipped });
      assert.deepEqual(after, before, from);
    }
  });

  it('when only GitHub\'s schedule starts the job, and it drops most runs, the same again', () => {
    const users = everyone();
    const runs = starts('2026-10-30', 5, (i) => (i * 7919) % 5 === 0);
    const { before, after } = replayBoth(users, runs);
    assert.deepEqual(after, before);
  });

  it('keeps a stored time that is due as it is, and changes any other', () => {
    const now = new Date('2026-10-12T13:07:00Z');
    const ts = (iso: string) => Timestamp.fromDate(new Date(iso));
    const later = new Date('2026-10-13T13:00:00Z');
    assert.equal(scheduleChanged(ts('2026-10-12T13:00:00Z'), now, now), false, 'due, and stored as due');
    assert.equal(scheduleChanged(ts('2026-10-13T13:00:00Z'), now, now), true, 'due now, stored for tomorrow');
    assert.equal(scheduleChanged(undefined, now, now), true, 'never stored');
    assert.equal(scheduleChanged(ts('2026-10-13T13:00:00Z'), later, now), false);
    assert.equal(scheduleChanged(ts('2026-10-12T13:00:00Z'), later, now), true, 'done today');
    assert.equal(scheduleChanged(ts('2026-10-13T13:00:00Z'), null, now), true, 'reminders off: removed');
    assert.equal(scheduleChanged(undefined, null, now), false);
    assert.equal(scheduleChanged('2026-10-13', later, now), true, 'not a timestamp');
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

describe('old join requests (after v1, SYNC-06)', () => {
  it('prunes pending and declined requests', () => {
    assert.equal(joinRequestPrunable('pending', 'h1', { waitingOn: 'h1' }), true);
    assert.equal(joinRequestPrunable('declined', 'h1', { waitingOn: 'h1' }), true);
    assert.equal(joinRequestPrunable(undefined, 'h1', null), true, 'a request without a status');
  });

  it('keeps an approved request while its sender\'s app still has to finish the join', () => {
    // The member hasn't opened the app since the owner approved: released builds need the request to get in.
    assert.equal(joinRequestPrunable('approved', 'h1', { waitingOn: 'h1' }), false);
  });

  it('prunes an approved request once the join is completed, dismissed or the account is gone', () => {
    assert.equal(joinRequestPrunable('approved', 'h1', { waitingOn: null }), true, 'joined, or dismissed');
    assert.equal(joinRequestPrunable('approved', 'h1', { waitingOn: 'h2' }), true, 'now waiting on another');
    assert.equal(joinRequestPrunable('approved', 'h1', null), true, 'account deleted');
  });

  it('reads the household a profile waits on', () => {
    const profile = (pendingJoin: unknown) => ({ get: (f: string) => (f === 'pendingJoin' ? pendingJoin : undefined) });
    assert.equal(waitingOn(profile({ householdId: 'h1', householdName: 'The Smiths', code: 'ABCDE23456' })), 'h1');
    assert.equal(waitingOn(profile(null)), null);
    assert.equal(waitingOn(profile({ householdId: 7 })), null);
    assert.equal(waitingOn(null), null);
  });
});
