import { BehaviorSubject } from 'rxjs';
import { describe, expect, it } from 'vitest';
import type { DeviceRegistry } from './household';
import { AppError } from '../core/errors';
import { DeviceSync, NotificationRules, type PushIdentity, type StoredFlag } from './notifications';
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

  it('explains notifications when the third dated item is saved, once (PantryViewModel)', () => {
    expect(NotificationRules.shouldExplain(true, 2, false)).toBe(true);
    expect(NotificationRules.shouldExplain(true, 5, false)).toBe(true);
    expect(NotificationRules.shouldExplain(true, 1, false)).toBe(false);
    expect(NotificationRules.shouldExplain(false, 4, false)).toBe(false);
    expect(NotificationRules.shouldExplain(true, 4, true)).toBe(false);
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
