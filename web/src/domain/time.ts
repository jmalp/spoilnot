// Dates (DESIGN: dates are 'YYYY-MM-DD' strings in the household's calendar) and an injectable clock, for the ported
// domain logic that used java.time.LocalDate and Clock in the app. 'YYYY-MM-DD' strings sort in date order.

export type LocalDate = string;
/** 'YYYY-MM'. */
export type YearMonth = string;

export interface Clock {
  /** Milliseconds since the epoch. */
  now(): number;
}

export const systemClock: Clock = { now: () => Date.now() };

const DAY_MS = 86_400_000;
const pad = (n: number, width = 2) => String(n).padStart(width, '0');

export function isLocalDate(value: unknown): value is LocalDate {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  return fromEpochDay(epochDay(value)) === value;
}

/** Days since 1970-01-01, by the calendar (no time zones or daylight saving involved). */
export function epochDay(date: LocalDate): number {
  const [y, m, d] = date.split('-').map(Number) as [number, number, number];
  return Math.round(Date.UTC(y, m - 1, d) / DAY_MS);
}

export function fromEpochDay(day: number): LocalDate {
  const date = new Date(day * DAY_MS);
  return `${pad(date.getUTCFullYear(), 4)}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
}

export const addDays = (date: LocalDate, days: number): LocalDate => fromEpochDay(epochDay(date) + days);

/** Calendar days from [from] to [to]; negative when [to] is earlier. */
export const daysBetween = (from: LocalDate, to: LocalDate): number => epochDay(to) - epochDay(from);

/** Today in the phone's calendar (LocalDate.now(clock)). */
export function today(clock: Clock): LocalDate {
  const now = new Date(clock.now());
  return `${pad(now.getFullYear(), 4)}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

export const yearMonthOf = (date: LocalDate): YearMonth => date.slice(0, 7);
export const dayOfMonth = (date: LocalDate): number => Number(date.slice(8, 10));
export const minDate = (a: LocalDate, b: LocalDate): LocalDate => (a < b ? a : b);

/** Comparators with nulls last, the app's compareBy(nullsLast()). */
export function nullsLast<T>(compare: (a: T, b: T) => number) {
  return (a: T | null | undefined, b: T | null | undefined): number => {
    if (a == null) return b == null ? 0 : 1;
    if (b == null) return -1;
    return compare(a, b);
  };
}

export const compareStrings = (a: string, b: string): number => (a < b ? -1 : a > b ? 1 : 0);

/** Sorts with a list of comparators applied in turn (compareBy(...).then(...)). */
export function sortedWith<T>(items: readonly T[], ...comparators: ((a: T, b: T) => number)[]): T[] {
  return [...items].sort((a, b) => {
    for (const compare of comparators) {
      const result = compare(a, b);
      if (result !== 0) return result;
    }
    return 0;
  });
}
