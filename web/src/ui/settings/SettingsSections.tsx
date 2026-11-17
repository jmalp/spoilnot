// The Settings sections (SettingsSections.kt and ColorThemePicker.kt in the app).
import { useSyncExternalStore, type CSSProperties } from 'react';
import { str, type StringId } from '../../core/i18n';
import { THEME_MODES, type ThemeMode } from '../../core/preferences';
import { usePreferences } from '../../core/services';
import type { Units } from '../../domain/account';
import { firstDayOfWeek, WEEK_STARTS, weekFirstDay, type WeekStart } from '../../domain/plan/week';
import { isDark, systemDark } from '../../theme/follow';
import { appliedTextScale } from '../../theme/textScale';
import type { ThemeId } from '../../theme/theme';
import { SCHEMES, THEME_IDS } from '../../theme/tokens.generated';
import { ActionRow, ConnectedButtonGroup, GroupHeading, Icon, ListGroup, PlainCard, SelectField } from '../kit';
import { weekdayName } from '../plan/components';
import './settings.css';

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

/** A choice's label on the Appearance card: the field label's style, in onSurfaceVariant (ChoiceLabel in the app). */
function ChoiceLabel({ text, id }: { text: string; id?: string }) {
  return (
    <span className="t-field-label c-on-surface-variant" id={id}>
      {text}
    </span>
  );
}

/**
 * Appearance (DESIGN 6.4, 5.6, 19.9) under its group heading, on a plain card: System, Light or Dark and metric or
 * imperial as connected button groups (a list of radio buttons from 1.5× text), the color themes, and on the web the
 * first day of Plan's week (5.9, 18.1; after v1, WEB-08).
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
    <section className="sn-settings__section">
      <GroupHeading text={str('settings_appearance')} />
      <PlainCard className="sn-settings__appearance" data-testid="card_appearance">
        <div className="sn-settings__choice">
          <ChoiceLabel text={str('settings_theme')} />
          <ConnectedButtonGroup
            label={str('settings_theme')}
            options={THEME_MODES}
            selected={themeMode}
            optionLabel={(mode) => str(`theme_${mode}` as StringId)}
            optionKey={(mode) => mode}
            optionTestId={(mode) => `theme_mode_${mode}`}
            onPick={onThemeMode}
          />
        </div>
        <ColorThemePicker selected={colorTheme} onPick={onColorTheme} />
        <div className="sn-settings__choice">
          <ChoiceLabel text={str('settings_units')} />
          <ConnectedButtonGroup
            label={str('settings_units')}
            options={UNITS}
            selected={units}
            optionLabel={(u) => str(`units_${u}` as StringId)}
            optionKey={(u) => u}
            optionTestId={(u) => `units_${u}`}
            onPick={onUnits}
          />
        </div>
        <SelectField
          label={str('web_week_start')}
          value={weekStart}
          options={WEEK_STARTS}
          optionLabel={weekStartLabel}
          onPick={onWeekStart}
          supportingText={str('web_week_start_help')}
          testId="week_start"
        />
      </PlainCard>
    </section>
  );
}

/** The theme's name in Settings. */
export const themeLabel = (theme: ThemeId): string => str(`colors_${theme}` as StringId);

/**
 * Settings > Appearance > Colors: one swatch per theme, showing its main, second and accent colors in the current
 * light or dark mode, in a grid of up to four (fewer as the text grows, so names wrap between words). The choice is
 * marked with a ring and a check on a dot of the page's text color, and read as a selected radio button, so it never
 * relies on color (DESIGN 6.4). The web app has no Wallpaper swatch (18.1).
 */
export function ColorThemePicker({ selected, onPick }: { selected: ThemeId; onPick: (theme: ThemeId) => void }) {
  const dark = useDarkTheme();
  // A cell grows with the text, so the longest word of a name still fits on its line.
  const cell = 76 * Math.min(2, Math.max(1, appliedTextScale()));
  return (
    <div className="sn-color-themes">
      <ChoiceLabel text={str('settings_colors')} id="color_themes_label" />
      <div
        role="radiogroup"
        aria-labelledby="color_themes_label"
        className="sn-color-themes__grid"
        style={{ '--sn-swatch-cell': `${cell}px` } as CSSProperties}
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
                    // On its own dot in the page's text color, so it shows on every part of every swatch.
                    <span className="sn-swatch__check">
                      <Icon name="round/check" size={18} />
                    </span>
                  )}
                </span>
              </span>
              <span className="sn-swatch__label t-chip-label">{themeLabel(theme)}</span>
            </button>
          );
        })}
      </div>
      <p className="sn-settings__help t-body-small">{str('settings_colors_summary')}</p>
    </div>
  );
}

/**
 * Data (DESIGN 13.4) under its group heading: Export household data as a row that starts the export. The app's Send
 * crash reports, beside it, has no web counterpart: the web app sends no crash reports (18.1).
 */
export function DataSection({ busy, onExport }: { busy: boolean; onExport: () => void }) {
  return (
    <section className="sn-settings__section">
      <GroupHeading text={str('settings_data')} />
      <ListGroup>
        <ActionRow
          icon="round/download"
          title={str('settings_export')}
          summary={str('settings_export_summary')}
          onClick={onExport}
          disabled={busy}
          testId="button_export"
        />
      </ListGroup>
    </section>
  );
}

/** About, as a row that opens it. The app's group also has the app tour, which the web app doesn't have (18.1). */
export function AboutGroup({ onOpenAbout }: { onOpenAbout: () => void }) {
  return (
    <ListGroup>
      <ActionRow
        icon="round/info"
        title={str('settings_about')}
        summary={str('settings_about_summary')}
        onClick={onOpenAbout}
        opensPage
        testId="row_about"
      />
    </ListGroup>
  );
}
