// Applies a color theme (DESIGN 6.4, 18.2) as CSS custom properties: --md-sys-color-<role> for the Material 3
// roles and --sn-status-<tone> for the fixed expiry colors. Light or dark follows the phone unless Settings says
// otherwise (follow.ts).
import { SCHEMES, STATUS_COLORS, THEME_IDS } from './tokens.generated';

export type ThemeId = (typeof THEME_IDS)[number];

/** The web app has no wallpaper colors (18.1), so its default is the Spoil Not green. */
export const DEFAULT_THEME: ThemeId = 'spoilnot';

export function isThemeId(value: unknown): value is ThemeId {
  return typeof value === 'string' && (THEME_IDS as readonly string[]).includes(value);
}

const kebab = (role: string) => role.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`);

/** The custom properties for [theme] in light or dark. */
export function themeVariables(theme: ThemeId, dark: boolean): Record<string, string> {
  const scheme = SCHEMES[theme][dark ? 'dark' : 'light'];
  const status = STATUS_COLORS[dark ? 'dark' : 'light'];
  return {
    ...Object.fromEntries(Object.entries(scheme).map(([role, color]) => [`--md-sys-color-${kebab(role)}`, color])),
    ...Object.fromEntries(Object.entries(status).map(([tone, color]) => [`--sn-status-${kebab(tone)}`, color])),
  };
}

export function applyTheme(theme: ThemeId, dark: boolean, root: HTMLElement = document.documentElement): void {
  for (const [name, value] of Object.entries(themeVariables(theme, dark))) root.style.setProperty(name, value);
  root.style.colorScheme = dark ? 'dark' : 'light';
  root.dataset.mode = dark ? 'dark' : 'light';
  // The status bar and Safari's chrome take the surface color, like the Android app's edge-to-edge bars.
  document
    .querySelector('meta[name="theme-color"]')
    ?.setAttribute('content', SCHEMES[theme][dark ? 'dark' : 'light'].surface);
}
