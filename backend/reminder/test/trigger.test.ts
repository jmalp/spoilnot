import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { describe, it } from 'node:test';
import vm from 'node:vm';

// The Apps Script that starts the reminder job on time (apps-script/trigger.gs, DESIGN 10.4), run in a sandbox with
// fakes for the Apps Script services and the clock.
const source = readFileSync(new URL('../apps-script/trigger.gs', import.meta.url), 'utf8');

type Fetch = { url: string; options: { method?: string; headers: Record<string, string>; payload?: string } };

/** GitHub's answer to a dispatch: a status code, or 'no answer' when the request throws (a timeout, a network error). */
type Answer = number | 'no answer';

function sandbox(options: { token?: string; status?: Answer } = {}) {
  const properties = new Map<string, string>();
  if (options.token !== undefined) properties.set('GITHUB_TOKEN', options.token);
  const fetches: Fetch[] = [];
  const emails: Array<{ to: string; subject: string; body: string }> = [];
  let status: Answer = options.status ?? 204;
  let now = new Date('2026-11-23T14:00:00Z');
  const RealDate = Date;
  class FakeDate extends RealDate {
    constructor(...args: unknown[]) {
      if (args.length === 0) super(now.getTime());
      else super(...(args as [string]));
    }
  }
  const context = vm.createContext({
    Date: FakeDate,
    Math,
    Number,
    String,
    JSON,
    Error,
    console: { log() {}, warn() {} },
    PropertiesService: {
      getScriptProperties: () => ({
        getProperty: (key: string) => properties.get(key) ?? null,
        setProperty: (key: string, value: string) => void properties.set(key, value),
      }),
    },
    UrlFetchApp: {
      fetch: (url: string, fetchOptions: Fetch['options']) => {
        fetches.push({ url, options: fetchOptions });
        if (status === 'no answer') throw new Error('Timeout: ' + url);
        const code = status;
        return { getResponseCode: () => code };
      },
    },
    MailApp: { sendEmail: (to: string, subject: string, body: string) => void emails.push({ to, subject, body }) },
    Session: { getEffectiveUser: () => ({ getEmail: () => 'owner@example.com' }) },
  });
  vm.runInContext(source, context);
  const script = context as unknown as {
    slotOf(now: Date): number;
    shouldStart(now: Date, lastSlot: number): boolean;
    tick(): void;
  };
  return {
    script,
    fetches,
    emails,
    properties,
    at(iso: string) {
      now = new RealDate(iso);
    },
    respond(code: Answer) {
      status = code;
    },
  };
}

/** Ticks every minute from `from` for `minutes` minutes, as the every-minute trigger does. */
function tickEveryMinute(s: ReturnType<typeof sandbox>, from: string, minutes: number) {
  const start = new Date(from).getTime();
  for (let i = 0; i < minutes; i++) {
    s.at(new Date(start + i * 60_000).toISOString());
    s.script.tick();
  }
}

describe('the on-time trigger (Apps Script)', () => {
  it('names the quarter hour a time falls in', () => {
    const { script } = sandbox();
    const minuteOf = (iso: string) => new Date(iso).getTime() / 60_000;
    assert.equal(script.slotOf(new Date('2026-11-23T09:07:30Z')), minuteOf('2026-11-23T09:00:00Z'));
    assert.equal(script.slotOf(new Date('2026-11-23T09:14:59Z')), minuteOf('2026-11-23T09:00:00Z'));
    assert.equal(script.slotOf(new Date('2026-11-23T09:15:00Z')), minuteOf('2026-11-23T09:15:00Z'));
    assert.equal(script.slotOf(new Date('2026-11-23T09:59:59Z')), minuteOf('2026-11-23T09:45:00Z'));
  });

  it('starts the job once per quarter hour, at its first minute', () => {
    const s = sandbox({ token: 'tok' });
    tickEveryMinute(s, '2026-11-23T14:00:10Z', 60);
    assert.equal(s.fetches.length, 4);
    assert.deepEqual(
      s.fetches.map((f) => f.url),
      Array(4).fill('https://api.github.com/repos/jmalp/spoilnot/actions/workflows/reminders.yml/dispatches'),
    );
    // A day of ticks: 96 runs.
    tickEveryMinute(s, '2026-11-23T15:00:10Z', 23 * 60);
    assert.equal(s.fetches.length, 96);
  });

  it('starts it for real on main: not the dry run that a run by hand defaults to', () => {
    const s = sandbox({ token: 'tok' });
    s.script.tick();
    const [fetch] = s.fetches;
    assert.equal(fetch?.options.method, 'post');
    assert.equal(fetch?.options.headers.Authorization, 'Bearer tok');
    assert.deepEqual(JSON.parse(fetch?.options.payload ?? ''), { ref: 'main', inputs: { dry_run: false } });
  });

  it('starts a quarter hour late rather than not at all when Apps Script skipped its first minutes', () => {
    const s = sandbox({ token: 'tok' });
    tickEveryMinute(s, '2026-11-23T14:06:00Z', 9);
    assert.equal(s.fetches.length, 1);
  });

  it('tries again the next minute after a failure, and emails the owner once a day', () => {
    const s = sandbox({ token: 'expired', status: 401 });
    tickEveryMinute(s, '2026-11-23T14:00:10Z', 30);
    assert.equal(s.fetches.length, 30);
    assert.equal(s.emails.length, 1);
    assert.equal(s.emails[0]?.to, 'owner@example.com');
    assert.equal(s.emails[0]?.subject, 'Spoil Not: reminders are not starting on time');
    assert.match(s.emails[0]?.body ?? '', /refused the token \(401\)/);
    assert.match(s.emails[0]?.body ?? '', /only on GitHub's own schedule/);
    // The next day it says so again; once the token works, one run per quarter hour again.
    tickEveryMinute(s, '2026-11-24T14:00:10Z', 2);
    assert.equal(s.emails.length, 2);
    s.respond(204);
    tickEveryMinute(s, '2026-11-24T14:15:10Z', 15);
    assert.equal(s.fetches.length, 33);
  });

  it('starts nothing without a token, and says so once a day', () => {
    const s = sandbox();
    tickEveryMinute(s, '2026-11-23T14:00:10Z', 30);
    assert.equal(s.fetches.length, 0);
    assert.equal(s.emails.length, 1);
    assert.match(s.emails[0]?.body ?? '', /GITHUB_TOKEN is missing/);
  });

  it('says at once when the token lost its access, or GitHub refused the request itself', () => {
    for (const [code, says] of [
      [403, /403 .*no longer has access to jmalp\/spoilnot/],
      [404, /404 .*no longer has access/],
      [422, /422 .*refused the request itself/],
    ] as const) {
      const s = sandbox({ token: 'tok', status: code });
      s.at('2026-11-25T15:45:05Z');
      s.script.tick();
      assert.equal(s.emails.length, 1, String(code));
      assert.match(s.emails[0]?.body ?? '', says);
    }
  });

  it("keeps quiet about GitHub's own errors while the run starts within 10 minutes (2026-11-25: 500s, +3 min)", () => {
    for (const answer of [500, 502, 503, 429, 'no answer'] as const) {
      const s = sandbox({ token: 'tok', status: answer });
      tickEveryMinute(s, '2026-11-25T15:45:22Z', 3);
      s.respond(204);
      tickEveryMinute(s, '2026-11-25T15:48:24Z', 12);
      assert.equal(s.fetches.length, 4, String(answer));
      assert.equal(s.emails.length, 0, String(answer));
    }
    // Even the last quiet minute: a run that starts 9 minutes late sends no email.
    const s = sandbox({ token: 'tok', status: 500 });
    tickEveryMinute(s, '2026-11-25T16:15:22Z', 9);
    s.respond(204);
    tickEveryMinute(s, '2026-11-25T16:24:22Z', 6);
    assert.equal(s.emails.length, 0);
    assert.equal(s.fetches.length, 10);
  });

  it("emails once when GitHub's side has kept a quarter hour's run from starting for 10 minutes", () => {
    const s = sandbox({ token: 'tok', status: 500 });
    tickEveryMinute(s, '2026-11-25T16:15:22Z', 10);
    assert.equal(s.emails.length, 0);
    tickEveryMinute(s, '2026-11-25T16:25:22Z', 30);
    assert.equal(s.fetches.length, 40);
    assert.equal(s.emails.length, 1);
    assert.equal(s.emails[0]?.subject, 'Spoil Not: GitHub is slow to start the reminders');
    assert.match(s.emails[0]?.body ?? '', /GitHub has answered 500 at every try since 16:15 UTC/);
    assert.match(s.emails[0]?.body ?? '', /not the token/);

    const quiet = sandbox({ token: 'tok', status: 'no answer' });
    tickEveryMinute(quiet, '2026-11-25T16:15:22Z', 11);
    assert.match(quiet.emails[0]?.body ?? '', /GitHub could not be reached at every try since 16:15 UTC/);
  });

  it("still says the same day when the token stops working after a warning about GitHub's side", () => {
    const s = sandbox({ token: 'tok', status: 503 });
    tickEveryMinute(s, '2026-11-25T16:15:22Z', 11);
    assert.equal(s.emails.length, 1);
    s.respond(401);
    tickEveryMinute(s, '2026-11-25T18:00:22Z', 30);
    assert.equal(s.emails.length, 2);
    assert.match(s.emails[1]?.body ?? '', /refused the token \(401\)/);
    // And each kind still at most once a day.
    s.respond(503);
    tickEveryMinute(s, '2026-11-25T19:00:22Z', 30);
    assert.equal(s.emails.length, 2);
  });
});
