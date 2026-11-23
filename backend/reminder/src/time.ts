// Calendar arithmetic for the reminder job. Dates are 'YYYY-MM-DD' strings with no time zone (DESIGN 8.1).

export interface LocalTime {
  /** The user's local calendar date, 'YYYY-MM-DD'. */
  date: string;
  /** Minutes after local midnight. */
  minutesOfDay: number;
}

/** One formatter per time zone (null: not a zone Intl knows, so UTC); the job converts times for every user it reads. */
const formatters = new Map<string, Intl.DateTimeFormat | null>();

function formatter(timeZone: string): Intl.DateTimeFormat | null {
  if (!formatters.has(timeZone)) {
    let f: Intl.DateTimeFormat | null;
    try {
      f = new Intl.DateTimeFormat('en-US', {
        timeZone,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hourCycle: 'h23',
      });
    } catch {
      f = null;
    }
    formatters.set(timeZone, f);
  }
  return formatters.get(timeZone)!;
}

/** The wall-clock time in an IANA time zone. Intl handles daylight saving (DESIGN 10.3). Falls back to UTC. */
export function toLocal(now: Date, timeZone: string | null | undefined): LocalTime {
  const f = formatter(timeZone || 'UTC') ?? formatter('UTC')!;
  const parts = f.formatToParts(now);
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

const MINUTE_MS = 60_000;
/** UTC offsets run from -12:00 to +14:00. */
const MAX_AHEAD_MS = 14 * 3_600_000;
const MAX_BEHIND_MS = 12 * 3_600_000;

/** Minutes since the epoch's local midnight that [now] reads as in [timeZone]: comparable across days. */
function wallMinutes(ms: number, timeZone: string | null | undefined): number {
  const local = toLocal(new Date(ms), timeZone);
  return epochDay(local.date) * 1440 + local.minutesOfDay;
}

/**
 * The first instant, to the minute, at which the clock in [timeZone] reads [date] at [minutes] (0 to 1439) or later,
 * the same fallback to UTC as [toLocal]. On a daylight saving day a time that happens twice gives the first; a time
 * the clocks skip gives the moment they skip to (02:30 on a spring-forward night in New York is 03:00 EDT).
 */
export function zonedInstant(date: string, minutes: number, timeZone: string | null | undefined): Date {
  const target = epochDay(date) * 1440 + minutes;
  const naive = target * MINUTE_MS;
  const offset = (ms: number) => wallMinutes(ms, timeZone) * MINUTE_MS - Math.floor(ms / MINUTE_MS) * MINUTE_MS;
  // The answer lies within a day of [naive]; every zone has at most one change of offset in that span, so the offsets
  // at its two ends (and the middle) are the candidates.
  const offsets = new Set([naive - MAX_AHEAD_MS, naive, naive + MAX_BEHIND_MS].map(offset));
  const candidates = [...offsets].map((o) => naive - o).sort((a, b) => a - b);
  const exact = candidates.find((t) => wallMinutes(t, timeZone) === target);
  if (exact !== undefined) return new Date(exact);
  // A skipped time: the clock jumps from before [target] to after it between two candidates; find the jump.
  const below = candidates.filter((t) => wallMinutes(t, timeZone) < target).pop();
  const above = candidates.find((t) => wallMinutes(t, timeZone) > target);
  if (below === undefined || above === undefined || above < below) return new Date(naive - MAX_AHEAD_MS); // early is safe
  let lo: number = below;
  let hi: number = above;
  while (hi - lo > MINUTE_MS) {
    const mid: number = lo + Math.floor((hi - lo) / (2 * MINUTE_MS)) * MINUTE_MS;
    if (wallMinutes(mid, timeZone) >= target) hi = mid;
    else lo = mid;
  }
  return new Date(hi);
}
