import { afterEach, describe, expect, it, vi } from 'vitest';
import { canAddToHomeScreen, isIos, isStandalone } from './install';

const IPHONE = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Version/17.5 Mobile Safari';

describe('install', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('Safari on an iPhone can add the app to the Home Screen; the installed app and other browsers cannot', () => {
    vi.stubGlobal('matchMedia', () => ({ matches: false }));
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(IPHONE);
    expect(isIos()).toBe(true);
    expect(canAddToHomeScreen()).toBe(true);
    vi.stubGlobal('matchMedia', (q: string) => ({ matches: q === '(display-mode: standalone)' }));
    expect(isStandalone()).toBe(true);
    expect(canAddToHomeScreen()).toBe(false);
    vi.stubGlobal('matchMedia', () => ({ matches: false }));
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (Linux; Android 16) Chrome/140 Mobile');
    expect(canAddToHomeScreen()).toBe(false);
  });

  it('an iPad, which reports itself as a Mac with touch, is iOS too', () => {
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)');
    vi.spyOn(navigator, 'platform', 'get').mockReturnValue('MacIntel');
    Object.defineProperty(navigator, 'maxTouchPoints', { configurable: true, value: 5 });
    expect(isIos()).toBe(true);
    Reflect.deleteProperty(navigator, 'maxTouchPoints');
  });
});
