import { describe, expect, it, vi } from 'vitest';
import { AppPreferences, PREFERENCE_KEYS } from './preferences';

describe('AppPreferences', () => {
  it('starts with System, the Spoil Not green and notifications not explained yet', () => {
    expect(new AppPreferences(null).get()).toEqual({
      themeMode: 'system',
      colorTheme: 'spoilnot',
      weekStart: 'auto',
      notificationExplainerShown: false,
      installHelpDismissed: false,
      aisleHintDone: false,
    });
  });

  it('keeps choices on the phone and tells listeners', () => {
    localStorage.clear();
    const prefs = new AppPreferences(localStorage);
    const listener = vi.fn();
    prefs.subscribe(listener);
    const before = prefs.get();
    prefs.set({ themeMode: 'dark', colorTheme: 'fiery' });
    expect(listener).toHaveBeenCalledTimes(1);
    expect(prefs.get()).not.toBe(before);
    expect(localStorage.getItem(PREFERENCE_KEYS.colorTheme)).toBe('fiery');
    prefs.set({ notificationExplainerShown: true, weekStart: 'monday', aisleHintDone: true });
    expect(localStorage.getItem(PREFERENCE_KEYS.aisleHintDone)).toBe('true');
    expect(localStorage.getItem(PREFERENCE_KEYS.weekStart)).toBe('monday');
    expect(new AppPreferences(localStorage).get()).toEqual({
      themeMode: 'dark',
      colorTheme: 'fiery',
      weekStart: 'monday',
      notificationExplainerShown: true,
      installHelpDismissed: false,
      aisleHintDone: true,
    });
  });

  it('reads an unknown or Android-only value as the default', () => {
    localStorage.clear();
    localStorage.setItem(PREFERENCE_KEYS.colorTheme, 'wallpaper');
    localStorage.setItem(PREFERENCE_KEYS.themeMode, 'sepia');
    localStorage.setItem(PREFERENCE_KEYS.weekStart, 'friday');
    expect(new AppPreferences(localStorage).get()).toMatchObject({
      themeMode: 'system',
      colorTheme: 'spoilnot',
      weekStart: 'auto',
    });
  });

  it('works without storage, for the session', () => {
    const prefs = new AppPreferences(null);
    prefs.set({ notificationExplainerShown: true });
    expect(prefs.get().notificationExplainerShown).toBe(true);
  });
});
