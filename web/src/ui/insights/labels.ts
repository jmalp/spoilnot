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

/**
 * The streak card's words (DESIGN 19.8): the [days] large ("15 days") over the [sentence] that says what they mean, or
 * only a sentence before a streak can be counted or on a day something was thrown away.
 */
export function streakParts(summary: InsightsSummary): { days: string | null; sentence: string } {
  const days = summary.daysWithoutWaste;
  if (days === null) return { days: null, sentence: str('insights_streak_none') };
  if (days === 0 && !summary.neverThrown) return { days: null, sentence: str('insights_streak_today') };
  return {
    days: plural('insights_streak_count', days, days),
    sentence: str(summary.neverThrown ? 'insights_streak_never_rest' : 'insights_streak_rest'),
  };
}
