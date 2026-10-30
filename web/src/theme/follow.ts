import type { AppPreferences, ThemeMode } from '../core/preferences';
import { applyTheme } from './theme';

/** Dark when Settings says Dark, or says System and the phone is dark. */
export const isDark = (mode: ThemeMode, systemDark: boolean): boolean =>
  mode === 'dark' || (mode === 'system' && systemDark);

const DARK_QUERY = '(prefers-color-scheme: dark)';

/** Whether the phone is in dark mode, as a store for useSyncExternalStore. */
export const systemDark = {
  get: (): boolean => matchMedia(DARK_QUERY).matches,
  subscribe(listener: () => void): () => void {
    const query = matchMedia(DARK_QUERY);
    query.addEventListener('change', listener);
    return () => query.removeEventListener('change', listener);
  },
};

/**
 * Applies the theme of Settings > Appearance now, again when it changes, and when the phone switches between light
 * and dark.
 */
export function followTheme(preferences: AppPreferences): void {
  const apply = () => {
    const { colorTheme, themeMode } = preferences.get();
    applyTheme(colorTheme, isDark(themeMode, systemDark.get()));
  };
  apply();
  preferences.subscribe(apply);
  systemDark.subscribe(apply);
}
