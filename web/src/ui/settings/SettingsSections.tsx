// The Settings cards (SettingsSections.kt and ColorThemePicker.kt in the app).
import { useSyncExternalStore } from 'react';
import { str, type StringId } from '../../core/i18n';
import { THEME_MODES, type ThemeMode } from '../../core/preferences';
import { usePreferences } from '../../core/services';
import type { Units } from '../../domain/account';
import { firstDayOfWeek, WEEK_STARTS, weekFirstDay, type WeekStart } from '../../domain/plan/week';
import { isDark, systemDark } from '../../theme/follow';
import { appliedTextScale } from '../../theme/textScale';
import type { ThemeId } from '../../theme/theme';
import { SCHEMES, THEME_IDS } from '../../theme/tokens.generated';
import { Button, Card, Icon, ListItem, Segmented, SelectField } from '../kit';
import { weekdayName } from '../plan/components';
import './settings.css';

function CardTitle({ text }: { text: string }) {
  return <h2 className="t-title-medium sn-settings__card-title">{text}</h2>;
}

/** Whether the app shows dark colors now: Settings' choice, or the phone's with System. */
function useDarkTheme(): boolean {
  const { themeMode } = usePreferences();
  return isDark(themeMode, useSyncExternalStore(systemDark.subscribe, systemDark.get));
}

const UNITS: readonly Units[] = ['metric', 'imperial'];

/** "Automatic (Sunday)", "Monday": Week starts on's choices, named in the phone's language. */
function weekStartLabel(choice: WeekStart): string {
  const locale = typeof navigator === 'undefined' ? 'en-US' : navigator.language;
  return choice === 'auto'
    ? str('web_week_start_auto', weekdayName(firstDayOfWeek(locale)))
    : weekdayName(weekFirstDay(choice, locale));
}

/**
 * System, Light or Dark; the color theme (DESIGN 6.4); metric or imperial (DESIGN 5.6); and on the web the first day
 * of Plan's week (5.9, 18.1; after v1, WEB-08).
 */
export function AppearanceCard({
  themeMode,
  colorTheme,
  units,
  weekStart,
  onThemeMode,
  onColorTheme,
  onUnits,
  onWeekStart,
}: {
  themeMode: ThemeMode;
  colorTheme: ThemeId;
  units: Units;
  weekStart: WeekStart;
  onThemeMode: (mode: ThemeMode) => void;
  onColorTheme: (theme: ThemeId) => void;
  onUnits: (units: Units) => void;
  onWeekStart: (weekStart: WeekStart) => void;
}) {
  return (
    <Card data-testid="card_appearance">
      <CardTitle text={str('settings_appearance')} />
      <div className="sn-settings__appearance">
        <Segmented
          label={str('settings_theme')}
          options={THEME_MODES}
          selected={themeMode}
          optionLabel={(mode) => str(`theme_${mode}` as StringId)}
          onPick={onThemeMode}
          testId="theme_mode"
        />
        <ColorThemePicker selected={colorTheme} onPick={onColorTheme} />
        <Segmented
          label={str('settings_units')}
          options={UNITS}
          selected={units}
          optionLabel={(u) => str(`units_${u}` as StringId)}
          onPick={onUnits}
          testId="units"
        />
        <SelectField
          label={str('web_week_start')}
          value={weekStart}
          options={WEEK_STARTS}
          optionLabel={weekStartLabel}
          onPick={onWeekStart}
          supportingText={str('web_week_start_help')}
          testId="week_start"
        />
      </div>
    </Card>
  );
}

/** The theme's name in Settings. */
export const themeLabel = (theme: ThemeId): string => str(`colors_${theme}` as StringId);

/**
 * Settings > Appearance > Colors: one swatch per theme, showing its main, second and accent colors in the current
 * light or dark mode. The choice is marked with a check and a ring, and read as a selected radio button, so it never
 * relies on color (DESIGN 6.4). The web app has no Wallpaper swatch (18.1).
 */
export function ColorThemePicker({ selected, onPick }: { selected: ThemeId; onPick: (theme: ThemeId) => void }) {
  const dark = useDarkTheme();
  // Wider with large text, so names wrap between words instead of inside them.
  const width = 76 * Math.min(2, Math.max(1, appliedTextScale()));
  return (
    <div className="sn-color-themes">
      <span id="color_themes_label" className="t-label-large">
        {str('settings_colors')}
      </span>
      <div
        role="radiogroup"
        aria-labelledby="color_themes_label"
        className="sn-color-themes__row"
        data-testid="color_themes"
      >
        {THEME_IDS.map((theme) => {
          const scheme = SCHEMES[theme][dark ? 'dark' : 'light'];
          const on = theme === selected;
          return (
            <button
              key={theme}
              type="button"
              role="radio"
              aria-checked={on}
              className="sn-swatch m3-state"
              style={{ width }}
              onClick={() => onPick(theme)}
              data-testid={`color_theme_${theme}`}
            >
              <span className={`sn-swatch__ring ${on ? 'sn-swatch__ring--selected' : ''}`}>
                <span
                  className="sn-swatch__colors"
                  style={{
                    // Top half the main color; the bottom split between the second and accent colors.
                    background: `linear-gradient(to bottom, ${scheme.primary} 50%, transparent 50%), linear-gradient(to right, ${scheme.secondaryContainer} 50%, ${scheme.tertiary} 50%)`,
                  }}
                >
                  {on && (
                    // On its own dot, so it shows on every part of the swatch.
                    <span
                      className="sn-swatch__check"
                      style={{ background: scheme.primary, color: scheme.onPrimary, borderColor: scheme.onPrimary }}
                    >
                      <Icon name="round/check" size={16} />
                    </span>
                  )}
                </span>
              </span>
              <span className="sn-swatch__label t-label-medium">{themeLabel(theme)}</span>
            </button>
          );
        })}
      </div>
      <p className="sn-settings__help t-body-small">{str('settings_colors_summary')}</p>
    </div>
  );
}

/** Export household data as JSON (DESIGN 13.4). */
export function DataCard({ busy, onExport }: { busy: boolean; onExport: () => void }) {
  return (
    <Card>
      <CardTitle text={str('settings_data')} />
      <ListItem
        headline={str('settings_export')}
        supporting={str('settings_export_summary')}
        leading={<Icon name="round/download" />}
      />
      <div className="sn-settings__card-action">
        <Button variant="outlined" large disabled={busy} onClick={onExport} data-testid="button_export">
          {str('action_export')}
        </Button>
      </div>
    </Card>
  );
}

/**
 * About. The app's card also has the crash reports switch and the app tour; the web app sends no crash reports and
 * has no tour (DESIGN 18.1).
 */
export function AboutCard({ onOpenAbout }: { onOpenAbout: () => void }) {
  return (
    <Card>
      <ListItem
        headline={str('settings_about')}
        supporting={str('settings_about_summary')}
        leading={<Icon name="round/info" />}
        trailing={<Icon name="round/keyboard_arrow_right" />}
        onClick={onOpenAbout}
        testId="row_about"
      />
    </Card>
  );
}
