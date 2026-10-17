// Calendar arithmetic for the reminder job. Dates are 'YYYY-MM-DD' strings with no time zone (DESIGN 8.1).

export interface LocalTime {
  /** The user's local calendar date, 'YYYY-MM-DD'. */
  date: string;
  /** Minutes after local midnight. */
  minutesOfDay: number;
}

/** The wall-clock time in an IANA time zone. Intl handles daylight saving (DESIGN 10.3). Falls back to UTC. */
export function toLocal(now: Date, timeZone: string | null | undefined): LocalTime {
  let parts: Intl.DateTimeFormatPart[];
  try {
    parts = new Intl.DateTimeFormat('en-US', {
      timeZone: timeZone || 'UTC',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    }).formatToParts(now);
  } catch {
    return toLocal(now, 'UTC');
  }
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '00';
  return {
    date: `${get('year')}-${get('month')}-${get('day')}`,
    minutesOfDay: Number(get('hour')) * 60 + Number(get('minute')),
  };
}

const DAY_MS = 86_400_000;

function epochDay(date: string): number {
  const [y, m, d] = date.split('-').map(Number);
  return Math.round(Date.UTC(y!, m! - 1, d!) / DAY_MS);
}

/** Whole calendar days from `from` to `to`; negative when `to` is earlier. */
export function daysBetween(from: string, to: string): number {
  return epochDay(to) - epochDay(from);
}

export function addDays(date: string, days: number): string {
  return new Date((epochDay(date) + days) * DAY_MS).toISOString().slice(0, 10);
}

export function isCalendarDate(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value);
}
