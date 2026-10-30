import { BehaviorSubject } from 'rxjs';
import { describe, expect, it } from 'vitest';
import type { DeviceRegistry } from './household';
import { DeviceSync, NotificationRules } from './notifications';
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

describe('DeviceSync', () => {
  it('registers once per signed-in user, and again when notifications are allowed', async () => {
    const synced: string[] = [];
    const registry: DeviceRegistry = {
      sync: async (uid) => void synced.push(uid),
      unregister: async () => undefined,
    };
    const session = new BehaviorSubject<Session>(LOADING);
    const sync = new DeviceSync(session, registry);
    sync.start();
    sync.start();
    session.next(inHousehold('sam'));
    session.next(inHousehold('sam', 'imperial'));
    expect(synced).toEqual(['sam']);
    await sync.permissionGranted();
    expect(synced).toEqual(['sam', 'sam']);
    session.next(SIGNED_OUT);
    await sync.permissionGranted();
    session.next(inHousehold('alex'));
    expect(synced).toEqual(['sam', 'sam', 'alex']);
    sync.stop();
  });

  it('a failed registration is tried again at the next start, not thrown', async () => {
    const registry: DeviceRegistry = {
      sync: async () => {
        throw new Error('offline');
      },
      unregister: async () => undefined,
    };
    const sync = new DeviceSync(new BehaviorSubject<Session>(inHousehold('sam')), registry);
    sync.start();
    await expect(sync.permissionGranted()).resolves.toBeUndefined();
  });
});
