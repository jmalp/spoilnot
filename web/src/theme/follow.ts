import { applyTheme, DEFAULT_THEME } from './theme';

/**
 * Applies the theme now and again when the phone switches between light and dark. Settings > Appearance (W6) will
 * choose the theme and System, Light or Dark.
 */
export function followSystemTheme(): void {
  const query = matchMedia('(prefers-color-scheme: dark)');
  const apply = () => applyTheme(DEFAULT_THEME, query.matches);
  apply();
  query.addEventListener('change', apply);
}
