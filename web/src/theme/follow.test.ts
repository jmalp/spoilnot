import { describe, expect, it, vi } from 'vitest';
import { AppPreferences } from '../core/preferences';
import { followTheme, isDark } from './follow';

describe('followTheme', () => {
  it('Light and Dark override the phone; System follows it', () => {
    expect(isDark('system', true)).toBe(true);
    expect(isDark('system', false)).toBe(false);
    expect(isDark('light', true)).toBe(false);
    expect(isDark('dark', false)).toBe(true);
  });

  it('applies the chosen theme and mode, and again when they change', () => {
    vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: () => undefined }));
    const prefs = new AppPreferences(null);
    followTheme(prefs);
    const root = document.documentElement;
    expect(root.dataset.mode).toBe('light');
    expect(root.style.getPropertyValue('--md-sys-color-primary')).toBe('#226a4c');
    prefs.set({ themeMode: 'dark', colorTheme: 'fiery' });
    expect(root.dataset.mode).toBe('dark');
    expect(root.style.getPropertyValue('--md-sys-color-primary')).not.toBe('#8ed5b0');
    vi.unstubAllGlobals();
  });
});
