// Small settings kept on this phone, not in the household (AppPreferences in the app, DESIGN 7.3): System, Light or
// Dark, the color theme, whether notifications were explained and whether the Add to Home Screen card was put away. Stored in localStorage; without it (private browsing)
// they last until the app closes.
import { DEFAULT_THEME, isThemeId, type ThemeId } from '../theme/theme';

/** Settings > Appearance (DESIGN 5.6). */
export type ThemeMode = 'system' | 'light' | 'dark';
export const THEME_MODES: readonly ThemeMode[] = ['system', 'light', 'dark'];

export type Preferences = {
  themeMode: ThemeMode;
  /** Settings > Appearance > Colors (DESIGN 6.4); the web app has no Wallpaper (18.1), so Spoil Not green. */
  colorTheme: ThemeId;
  /** The notification explanation (DESIGN 5.4) was answered or not needed; it is not shown again. */
  notificationExplainerShown: boolean;
  /** Home's Add to Home Screen card (DESIGN 18.1) was answered Not now. */
  installHelpDismissed: boolean;
};

export const PREFERENCE_KEYS = {
  themeMode: 'spoilnot.themeMode',
  colorTheme: 'spoilnot.colorTheme',
  notificationExplainerShown: 'spoilnot.notificationExplainerShown',
  installHelpDismissed: 'spoilnot.installHelpDismissed',
} as const;

function defaultStorage(): Storage | null {
  try {
    return localStorage;
  } catch {
    return null;
  }
}

/** The preferences as a store for useSyncExternalStore: [get] returns the same object until something changes. */
export class AppPreferences {
  private value: Preferences;
  private readonly listeners = new Set<() => void>();

  constructor(private readonly storage: Storage | null = defaultStorage()) {
    const read = (key: string) => {
      try {
        return storage?.getItem(key) ?? null;
      } catch {
        return null;
      }
    };
    const mode = read(PREFERENCE_KEYS.themeMode);
    const theme = read(PREFERENCE_KEYS.colorTheme);
    this.value = {
      themeMode: THEME_MODES.includes(mode as ThemeMode) ? (mode as ThemeMode) : 'system',
      // A theme only Android has (Wallpaper) shows as the Spoil Not green.
      colorTheme: isThemeId(theme) ? theme : DEFAULT_THEME,
      notificationExplainerShown: read(PREFERENCE_KEYS.notificationExplainerShown) === 'true',
      installHelpDismissed: read(PREFERENCE_KEYS.installHelpDismissed) === 'true',
    };
  }

  readonly get = (): Preferences => this.value;

  readonly subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  set(change: Partial<Preferences>): void {
    this.value = { ...this.value, ...change };
    for (const [key, value] of Object.entries(change)) {
      try {
        this.storage?.setItem(PREFERENCE_KEYS[key as keyof Preferences], String(value));
      } catch {
        // Full or unavailable storage: the choice lasts until the app closes.
      }
    }
    this.listeners.forEach((listener) => listener());
  }
}
