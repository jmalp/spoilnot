// Small pantry rules (DESIGN 5.2, 8.1), ported from NameKey, NaturalOrder, ExpiryStatus and ShelfLife.
import { addDays, daysBetween, type LocalDate } from '../time';

/**
 * Whitespace, one definition for both apps (after v1, WEB-06): Unicode's White_Space characters (tab to carriage
 * return, space, U+0085, no-break space, U+1680, U+2000 to U+200A, line and paragraph separators, U+202F, U+205F, the
 * full-width space U+3000) and U+FEFF. Written out because JavaScript's `\s` lacks U+0085 and Java's is ASCII only.
 * Android's `NameKey.WHITESPACE` is the same class.
 */
export const WHITESPACE =
  '\\t\\n\\u000B\\f\\r \\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF';
const ONE_SPACE = new RegExp(`[${WHITESPACE}]`, 'g');
const SPACES = new RegExp(`[${WHITESPACE}]+`, 'g');

/** Lowercased, trimmed, whitespace collapsed, diacritics removed: used to match and de-duplicate. */
export const NameKey = {
  of: (name: string): string =>
    NameKey.clean(
      name
        .normalize('NFD')
        .replace(/\p{Mn}+/gu, '')
        .toLowerCase(),
    ),
  /** Display form of a typed name: trimmed with whitespace collapsed, case kept. */
  clean: (name: string): string => name.replace(SPACES, ' ').replace(/^ +| +$/g, ''),
  /**
   * [text] with every whitespace character replaced by a plain space, keeping its length, so the parsers' patterns
   * read the same text on both apps and their positions still point into the original.
   */
  plainSpaces: (text: string): string => text.replace(ONE_SPACE, ' '),
};

/** Names in the order people expect: case and accents ignored, numbers by value ("Bag 2" before "Bag 10"). */
export function naturalOrder(a: string, b: string): number {
  const x = NameKey.of(a).match(/\d+|\D+/g) ?? [];
  const y = NameKey.of(b).match(/\d+|\D+/g) ?? [];
  for (let i = 0; i < Math.min(x.length, y.length); i++) {
    const p = x[i]!;
    const q = y[i]!;
    const c = /\d/.test(p[0]!) && /\d/.test(q[0]!) ? compareNumbers(p, q) : compare(p, q);
    if (c !== 0) return c;
  }
  return x.length - y.length || compare(a, b);
}

const compare = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0);

/** By value without overflow: fewer digits (after leading zeros) is smaller. */
function compareNumbers(p: string, q: string): number {
  const a = p.replace(/^0+/, '');
  const b = q.replace(/^0+/, '');
  return a.length !== b.length ? a.length - b.length : compare(a, b);
}

/** Derived from the expiry date in the phone's calendar (DESIGN 5.2 status rules). Soon is 2 to 3 days, Week 4 to 7. */
export type ExpiryStatus = 'expired' | 'today' | 'tomorrow' | 'soon' | 'week' | 'fresh' | 'noDate';

export const Expiry = {
  of(expiry: LocalDate | null, today: LocalDate): ExpiryStatus {
    if (expiry === null) return 'noDate';
    const days = daysBetween(today, expiry);
    if (days < 0) return 'expired';
    if (days === 0) return 'today';
    if (days === 1) return 'tomorrow';
    if (days <= 3) return 'soon';
    if (days <= 7) return 'week';
    return 'fresh';
  },
  /** Calendar days from [today] to [expiry]; negative once expired. */
  daysUntil: (expiry: LocalDate, today: LocalDate): number => daysBetween(today, expiry),
};

/**
 * Learning shelf life (DESIGN 5.2): the median of (expiry date minus date added) over the last 5 batches, used only to
 * pre-fill the expiry date.
 */
export const ShelfLife = {
  WINDOW: 5,
  /** The recent shelf lives after a batch added on [added] with [expiry]. */
  record(recent: number[], added: LocalDate, expiry: LocalDate | null): number[] {
    if (expiry === null) return recent;
    const days = daysBetween(added, expiry);
    if (days < 0) return recent;
    return [...recent, days].slice(-this.WINDOW);
  },
  /** Median, rounding the mean of the two middle values half up for an even count; null when empty. */
  median(values: number[]): number | null {
    if (values.length === 0) return null;
    const sorted = [...values].sort((a, b) => a - b);
    const mid = Math.floor(sorted.length / 2);
    return sorted.length % 2 === 1 ? sorted[mid]! : Math.floor((sorted[mid - 1]! + sorted[mid]! + 1) / 2);
  },
  suggestExpiry: (typicalDays: number | null, today: LocalDate): LocalDate | null =>
    typicalDays === null ? null : addDays(today, typicalDays),
};
