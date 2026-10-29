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
});
