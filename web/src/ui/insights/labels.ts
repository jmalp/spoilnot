import { plural, str } from '../../core/i18n';
import type { InsightsSummary, Mood } from '../../domain/insights/insights';
import type { YearMonth } from '../../domain/time';

/** A whole percentage; 99.6 % shows as 99, not 100, so "100 %" means nothing was thrown away. */
export function percent(share: number | null): number {
  const value = (share ?? 0) * 100;
  return value > 99 && value < 100 ? 99 : Math.round(value);
}

/** "Oct" (short), "October", or "October 2025" for a full name outside [thisMonth]'s year. */
export function monthName(month: YearMonth, style: 'short' | 'long', thisMonth: YearMonth): string {
  const [year, m] = month.split('-').map(Number) as [number, number];
  const name = new Intl.DateTimeFormat(undefined, { month: style, timeZone: 'UTC' }).format(Date.UTC(year, m - 1, 1));
  return style === 'short' || month.slice(0, 4) === thisMonth.slice(0, 4) ? name : `${name} ${year}`;
}

export function moodText(mood: Mood): string {
  switch (mood) {
    case 'nothingYet':
      return str('insights_mood_nothing');
    case 'great':
      return str('insights_mood_great');
    case 'good':
      return str('insights_mood_good');
    case 'okay':
      return str('insights_mood_okay');
    case 'low':
      return str('insights_mood_low');
  }
}

export function streakText(summary: InsightsSummary): string {
  const days = summary.daysWithoutWaste;
  if (days === null) return str('insights_streak_none');
  if (summary.neverThrown) return plural('insights_streak_never', days, days);
  if (days === 0) return str('insights_streak_today');
  return plural('insights_streak_days', days, days);
}
