import { afterEach, describe, expect, it, vi } from 'vitest';
import { pushStatus, requestPushPermission, showTestNotification } from './push';

/** The Notification API of an iPhone web app, with the system's answer and the notifications shown. */
const shown: string[] = [];
const FakeNotification = Object.assign(
  function Notification(title: string) {
    shown.push(title);
  },
  {
    permission: 'default' as NotificationPermission,
    requestPermission: vi.fn(async () => {
      FakeNotification.permission = 'granted';
      return FakeNotification.permission;
    }),
  },
);

/** An iPhone web app on the Home Screen: Notification and PushManager exist. */
function installedIphone() {
  FakeNotification.permission = 'default';
  shown.length = 0;
  vi.stubGlobal('Notification', FakeNotification);
  vi.stubGlobal('PushManager', function PushManager() {});
  Object.defineProperty(navigator, 'serviceWorker', {
    configurable: true,
    value: { getRegistration: async () => undefined },
  });
}

describe('push status', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    Reflect.deleteProperty(navigator, 'serviceWorker');
  });

  it('an iPhone in Safari must add the app to the Home Screen first; other browsers without push cannot', () => {
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X)');
    expect(pushStatus()).toBe('install');
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (X11; Linux x86_64)');
    expect(pushStatus()).toBe('unsupported');
    vi.restoreAllMocks();
  });

  it("follows the system's answer, and asks only when it was not asked yet", async () => {
    installedIphone();
    expect(pushStatus()).toBe('default');
    expect(await requestPushPermission()).toBe('granted');
    expect(FakeNotification.requestPermission).toHaveBeenCalledTimes(1);
    expect(await requestPushPermission()).toBe('granted');
    expect(FakeNotification.requestPermission).toHaveBeenCalledTimes(1);
    FakeNotification.permission = 'denied';
    expect(pushStatus()).toBe('denied');
  });

  it('the test notification shows only with the permission', async () => {
    installedIphone();
    expect(await showTestNotification()).toBe(false);
    FakeNotification.permission = 'granted';
    expect(await showTestNotification()).toBe(true);
    expect(shown).toEqual(['Test notification']);
  });
});
