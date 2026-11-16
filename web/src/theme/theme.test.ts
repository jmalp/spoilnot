import { describe, expect, it } from 'vitest';
import { SCHEMES, THEME_IDS } from './tokens.generated';
import { applyTheme, isThemeId, themeVariables } from './theme';

describe('theme', () => {
  it('has the Android themes in Settings order, each with every role in light and dark', () => {
    expect(THEME_IDS).toEqual([
      'spoilnot',
      'fiery',
      'summer_fun',
      'enchanted_forest',
      'watermelon_sorbet',
      'contrast_pop',
    ]);
    const roles = Object.keys(SCHEMES.spoilnot.light).sort();
    expect(roles).toHaveLength(36);
    for (const id of THEME_IDS) {
      expect(Object.keys(SCHEMES[id].light).sort()).toEqual(roles);
      expect(Object.keys(SCHEMES[id].dark).sort()).toEqual(roles);
    }
  });

  it('exposes Material 3 roles and status colors as CSS variables', () => {
    const vars = themeVariables('spoilnot', false);
    expect(vars['--md-sys-color-primary']).toBe('#226a4c');
    expect(vars['--md-sys-color-surface-container-highest']).toBe('#dee4de');
    expect(vars['--sn-status-no-date']).toBe('#404943');
    applyTheme('spoilnot', true);
    expect(document.documentElement.style.getPropertyValue('--md-sys-color-primary')).toBe('#8ed5b0');
    expect(document.documentElement.dataset.mode).toBe('dark');
    expect(isThemeId('wallpaper')).toBe(false);
  });

  it('sets the page and row surfaces from different roles in light and dark (DESIGN 19.2)', () => {
    const light = themeVariables('spoilnot', false);
    expect(light['--sn-surface-page']).toBe(light['--md-sys-color-surface-container-low']);
    expect(light['--sn-surface-row']).toBe(light['--md-sys-color-surface-container-lowest']);
    expect(light['--sn-surface-tile']).toBe(light['--md-sys-color-surface-container']);
    const dark = themeVariables('spoilnot', true);
    expect(dark['--sn-surface-page']).toBe(dark['--md-sys-color-surface']);
    expect(dark['--sn-surface-row']).toBe(dark['--md-sys-color-surface-container']);
    expect(dark['--sn-surface-nested-row']).toBe(dark['--md-sys-color-surface-container-low']);
    expect(dark['--sn-surface-field']).toBe(dark['--md-sys-color-surface-container-high']);
  });

  it('keeps rows lighter than the page in every theme, light and dark', () => {
    const luminance = (hex: string) => {
      const channel = (i: number) => {
        const c = parseInt(hex.slice(i, i + 2), 16) / 255;
        return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
      };
      return 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5);
    };
    for (const id of THEME_IDS) {
      for (const dark of [false, true]) {
        const vars = themeVariables(id, dark);
        expect(luminance(vars['--sn-surface-row']!), `${id} ${dark ? 'dark' : 'light'}`).toBeGreaterThan(
          luminance(vars['--sn-surface-page']!),
        );
      }
    }
  });
});
