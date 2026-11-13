import { BehaviorSubject } from 'rxjs';
import { describe, expect, it } from 'vitest';
import type { DeviceRegistry } from './household';
import { AppError } from '../core/errors';
import {
  DeviceSync,
  NotificationPrompt,
  NotificationRules,
  YoureSet,
  type PushIdentity,
  type ReminderAccess,
  type StoredFlag,
} from './notifications';
import { batch, FIXED_CLOCK, TODAY } from './pantry/fixtures';
import { addDays, today } from './time';
import { LOADING, SIGNED_OUT, type Session } from './session';

const user = (uid: string) => ({ uid, email: `${uid}@example.com`, displayName: uid, emailVerified: true });
const inHousehold = (uid: string, units: 'metric' | 'imperial' = 'metric'): Session => ({
  kind: 'inHousehold',
  user: user(uid),
  displayName: uid,
  householdId: 'h1',
  role: 'owner',
  units,
});

describe('NotificationRules', () => {
  it("reads the stored prefs with the app's defaults", () => {
    expect(NotificationRules.read(undefined)).toEqual({
      enabled: true,
      digestMinutes: 540,
      stages: [3, 2, 1, 0],
      expiredMode: 'once',
      cooking: true,
    });
    expect(
      NotificationRules.read({ enabled: false, digestMinutes: 1200, stages: [0, 2, 2, 7], expiredMode: 'never' }),
    ).toEqual({ enabled: false, digestMinutes: 1200, stages: [2, 0], expiredMode: 'never', cooking: true });
    expect(NotificationRules.read({ expiredMode: 'sometimes', cooking: false })).toMatchObject({
      expiredMode: 'once',
      cooking: false,
    });
  });

  it('offers whole hours from 5:00 to 21:00, the last the job sends at', () => {
    expect(NotificationRules.HOURS[0]).toBe(5);
    expect(NotificationRules.HOURS.at(-1)).toBe(21);
    expect(NotificationRules.hour({ ...NotificationRules.read({}), digestMinutes: 9 * 60 + 30 })).toBe(9);
  });
});

/** DESIGN 5.4 after v1 (review UX-02, CODE-10): when this phone explains reminders and asks (NotificationPromptTest). */
describe('NotificationPrompt', () => {
  const blocked: ReminderAccess = { permission: 'default', remindersOn: true, explained: false };

  it('a household that opens with dated food asks at once', () => {
    // A member who joined, or a new phone: one dated item is enough.
    expect(NotificationPrompt.shouldAsk(blocked, 1, 1)).toBe(true);
    // It still asks if the food was used up since the household opened.
    expect(NotificationPrompt.shouldAsk(blocked, 2, 0)).toBe(true);
  });

  it('a household that opens with none asks at the third dated item, whoever adds it', () => {
    expect(NotificationPrompt.shouldAsk(blocked, 0, 0)).toBe(false);
    expect(NotificationPrompt.shouldAsk(blocked, 0, 1)).toBe(false);
    expect(NotificationPrompt.shouldAsk(blocked, 0, 2)).toBe(false);
    expect(NotificationPrompt.shouldAsk(blocked, 0, NotificationPrompt.DATED_TO_ASK)).toBe(true);
    expect(NotificationPrompt.shouldAsk(blocked, 0, 7)).toBe(true);
  });

  it('nothing is asked before the household has loaded from the server', () => {
    // A new phone's cache is empty at first: that is not a new household.
    expect(NotificationPrompt.shouldAsk(blocked, null, 5)).toBe(false);
  });

  it('it asks once, only while the system can still ask and only when the member wants reminders', () => {
    expect(NotificationPrompt.shouldAsk({ ...blocked, explained: true }, 3, 3)).toBe(false);
    expect(NotificationPrompt.shouldAsk({ ...blocked, permission: 'granted' }, 3, 3)).toBe(false);
    // A web app can't show the system's question again once refused.
    expect(NotificationPrompt.shouldAsk({ ...blocked, permission: 'denied' }, 3, 3)).toBe(false);
    // Safari outside the Home Screen waits for the installed app; a browser without web push never asks.
    expect(NotificationPrompt.shouldAsk({ ...blocked, permission: 'install' }, 3, 3)).toBe(false);
    expect(NotificationPrompt.shouldAsk({ ...blocked, permission: 'unsupported' }, 3, 3)).toBe(false);
    expect(NotificationPrompt.shouldAsk({ ...blocked, remindersOn: false }, 3, 3)).toBe(false);
    // The member's settings have not loaded yet: wait rather than ask someone who turned reminders off.
    expect(NotificationPrompt.shouldAsk({ ...blocked, remindersOn: null }, 3, 3)).toBe(false);
  });

  it('a phone whose answer is already in is marked explained without asking', () => {
    expect(NotificationPrompt.explainedWithoutAsking({ ...blocked, permission: 'granted' })).toBe(true);
    expect(NotificationPrompt.explainedWithoutAsking({ ...blocked, permission: 'denied' })).toBe(true);
    expect(NotificationPrompt.explainedWithoutAsking(blocked)).toBe(false);
    expect(NotificationPrompt.explainedWithoutAsking({ ...blocked, permission: 'install' })).toBe(false);
    expect(NotificationPrompt.explainedWithoutAsking({ ...blocked, permission: 'granted', explained: true })).toBe(
      false,
    );
  });

  it('Home says reminders are off once the explanation was answered and the phone still blocks them', () => {
    const answered = { ...blocked, explained: true };
    expect(NotificationPrompt.offOnThisPhone(answered)).toBe(true);
    expect(NotificationPrompt.offOnThisPhone({ ...answered, permission: 'denied' })).toBe(true);
    // Before the explanation, the explanation itself asks.
    expect(NotificationPrompt.offOnThisPhone(blocked)).toBe(false);
    expect(NotificationPrompt.offOnThisPhone({ ...answered, permission: 'granted' })).toBe(false);
    // Safari has its Add to Home Screen card; a browser without web push has nothing to turn on.
    expect(NotificationPrompt.offOnThisPhone({ ...answered, permission: 'install' })).toBe(false);
    expect(NotificationPrompt.offOnThisPhone({ ...answered, permission: 'unsupported' })).toBe(false);
    // Turned off in Settings on purpose: Settings already says Off.
    expect(NotificationPrompt.offOnThisPhone({ ...answered, remindersOn: false })).toBe(false);
    expect(NotificationPrompt.offOnThisPhone({ ...answered, remindersOn: null })).toBe(false);
  });

  it('Settings says Off on this phone when the reminder is on but this phone cannot show it', () => {
    expect(NotificationPrompt.blockedHere('default', true)).toBe(true);
    expect(NotificationPrompt.blockedHere('denied', true)).toBe(true);
    expect(NotificationPrompt.blockedHere('install', true)).toBe(true);
    expect(NotificationPrompt.blockedHere('granted', true)).toBe(false);
    expect(NotificationPrompt.blockedHere('denied', false)).toBe(false);
    expect(NotificationPrompt.blockedHere('denied', null)).toBe(false);
  });

  it("dated food is what is in stock and not yet expired, on the clock's day", () => {
    const batches = [
      batch('a', 'milk', { inDays: 0 }),
      batch('b', 'eggs', { inDays: 3 }),
      batch('c', 'yogurt', { inDays: -1 }),
      batch('d', 'rice', { inDays: null }),
      batch('e', 'cheese', { inDays: 5, quantity: 0 }),
    ];
    expect(NotificationPrompt.datedFood(batches, TODAY)).toBe(2);
    // The next day, today's milk has expired.
    expect(NotificationPrompt.datedFood(batches, addDays(TODAY, 1))).toBe(1);
    // Callers pass today from the injected clock, in the phone's zone.
    expect(NotificationPrompt.datedFood(batches, today(FIXED_CLOCK))).toBe(2);
  });
});

/** "You're set" after Turn on (DESIGN 5.4; owner's decision 2): the time and tomorrow's reminder (YoureSetTest). */
describe('YoureSet', () => {
  const prefs = NotificationRules.read(undefined);

  it("names what tomorrow's reminder picks, soonest first, one name per product", () => {
    const batches = [
      // Tomorrow is 2026-10-13: these are 0, 1 and 3 days from it.
      batch('s', 'spinach', { inDays: 2 }),
      batch('c', 'chicken', { inDays: 1 }),
      batch('c2', 'chicken', { inDays: 3 }),
      batch('y', 'yogurt', { inDays: 4 }),
      // Expires today: expired tomorrow, left out. Too far, undated or used up: left out.
      batch('m', 'milk', { inDays: 0 }),
      batch('r', 'rice', { inDays: 5 }),
      batch('f', 'flour', { inDays: null }),
      batch('e', 'eggs', { inDays: 1, quantity: 0 }),
    ];
    expect(YoureSet.of(prefs, batches, TODAY)).toEqual({
      hour: 9,
      tomorrow: ['Chicken', 'Spinach', 'Yogurt'],
      more: 0,
    });
  });

  it("the member's own stages and hour", () => {
    const batches = [batch('c', 'chicken', { inDays: 1 }), batch('s', 'spinach', { inDays: 2 })];
    // Only "the day it expires" and "1 day before": tomorrow names the chicken (0) and the spinach (1).
    const content = YoureSet.of({ ...prefs, digestMinutes: 18 * 60, stages: [1, 0] }, batches, TODAY);
    expect(content).toEqual({ hour: 18, tomorrow: ['Chicken', 'Spinach'], more: 0 });
    // Only 3 days before: neither.
    expect(YoureSet.of({ ...prefs, stages: [3] }, batches, TODAY).tomorrow).toEqual([]);
  });

  it('more than three products are counted', () => {
    const batches = [1, 2, 3, 4, 5].map((i) => batch(`b${i}`, `p${i}`, { inDays: 1, name: `Item ${i}` }));
    expect(YoureSet.of(prefs, batches, TODAY)).toEqual({ hour: 9, tomorrow: ['Item 1', 'Item 2', 'Item 3'], more: 2 });
  });

  it('nothing due tomorrow', () => {
    expect(YoureSet.of(prefs, [batch('r', 'rice', { inDays: 30 })], TODAY)).toEqual({ hour: 9, tomorrow: [], more: 0 });
  });
});

/** Lets promises and subscriptions settle. */
const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

/** A fake push identity and flag that log to [events]; [online] decides whether a token delete succeeds. */
function fakes(events: string[]) {
  let flag = false;
  const state = { online: true };
  const push: PushIdentity = {
    installId: () => 'browser-1',
    token: async () => 'token-1',
    deleteToken: async () => {
      events.push(state.online ? 'deleteToken' : 'deleteToken failed');
      if (!state.online) throw new AppError('offline');
    },
  };
  const tokenToDelete: StoredFlag = { get: () => flag, set: (on) => void (flag = on) };
  const registry: DeviceRegistry = {
    sync: async (uid) => void events.push(`sync ${uid}`),
    unregister: async (uid) => void events.push(`unregister ${uid}`),
  };
  return { push, tokenToDelete, registry, state, flag: () => flag };
}

describe('DeviceSync', () => {
  it('registers once per signed-in user, and again when notifications are allowed', async () => {
    const events: string[] = [];
    const { push, tokenToDelete, registry } = fakes(events);
    const session = new BehaviorSubject<Session>(LOADING);
    const sync = new DeviceSync(session, registry, push, tokenToDelete);
    sync.start();
    sync.start();
    session.next(inHousehold('sam'));
    session.next(LOADING);
    session.next(inHousehold('sam', 'imperial'));
    await settle();
    expect(events).toEqual(['sync sam']);
    await sync.permissionGranted();
    expect(events).toEqual(['sync sam', 'sync sam']);
    session.next(SIGNED_OUT);
    await settle();
    await sync.permissionGranted();
    session.next(inHousehold('alex'));
    await settle();
    expect(events).toEqual(['sync sam', 'sync sam', 'deleteToken', 'sync alex']);
    sync.stop();
  });

  it('a start without a user and nothing to delete does nothing', async () => {
    const events: string[] = [];
    const { push, tokenToDelete, registry } = fakes(events);
    new DeviceSync(new BehaviorSubject<Session>(SIGNED_OUT), registry, push, tokenToDelete).start();
    await settle();
    expect(events).toEqual([]);
  });

  it('a token that could not be deleted is deleted at the next start, before registering', async () => {
    const events: string[] = [];
    const f = fakes(events);
    f.state.online = false;
    const session = new BehaviorSubject<Session>(inHousehold('sam'));
    const first = new DeviceSync(session, f.registry, f.push, f.tokenToDelete);
    first.start();
    session.next(SIGNED_OUT);
    await settle();
    expect(f.flag()).toBe(true);
    first.stop();

    // The next start, online, with someone signing in.
    f.state.online = true;
    const next = new DeviceSync(new BehaviorSubject<Session>(inHousehold('alex')), f.registry, f.push, f.tokenToDelete);
    next.start();
    await settle();
    expect(events).toEqual(['sync sam', 'deleteToken failed', 'deleteToken', 'sync alex']);
    expect(f.flag()).toBe(false);
  });

  it('a token delete that does not answer in time waits for the next start', async () => {
    const events: string[] = [];
    const f = fakes(events);
    const push: PushIdentity = { ...f.push, deleteToken: () => new Promise(() => events.push('deleteToken')) };
    const session = new BehaviorSubject<Session>(inHousehold('sam'));
    new DeviceSync(session, f.registry, push, f.tokenToDelete, 10).start();
    session.next(SIGNED_OUT);
    await new Promise((resolve) => setTimeout(resolve, 30));
    expect(events).toEqual(['sync sam', 'deleteToken']);
    expect(f.flag()).toBe(true);
  });

  it('a failed registration is tried again at the next start, not thrown', async () => {
    const f = fakes([]);
    const registry: DeviceRegistry = {
      sync: async () => {
        throw new Error('offline');
      },
      unregister: async () => undefined,
    };
    const sync = new DeviceSync(new BehaviorSubject<Session>(inHousehold('sam')), registry, f.push, f.tokenToDelete);
    sync.start();
    await expect(sync.permissionGranted()).resolves.toBeUndefined();
  });
});
