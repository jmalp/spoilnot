// Reading another client's documents (after v1, DESIGN 8.6, review WEB-10; core/firebase/Fields.kt in the app).
// A field of another type reads as missing, so the mapper's default applies, and mapEach skips a document that still
// fails to map. Before, a list field holding text threw ("7".filter is not a function), and since one pantry flow maps
// every product, one such document kept Pantry and Home spinning on that phone, even after a reload.
import type { DocumentSnapshot } from 'firebase/firestore';
import { isLocalDate, type LocalDate } from '../domain/time';

/** The value as text, or null for anything else. */
export const str = (value: unknown): string | null => (typeof value === 'string' ? value : null);

/** The value as a number, or null for anything else. */
export const num = (value: unknown): number | null => (typeof value === 'number' ? value : null);

/** The value as a whole number (a fraction is cut, as the app's `getLong`), or null for anything else. */
export const int = (value: unknown): number | null => (typeof value === 'number' ? Math.trunc(value) : null);

/** A 'YYYY-MM-DD' date, or null for anything else. */
export const date = (value: unknown): LocalDate | null => (isLocalDate(value) ? value : null);

/** The value as a list, or an empty one for anything else. */
export const list = (value: unknown): unknown[] => (Array.isArray(value) ? value : []);

/** The text entries of a list; entries of another type are left out. */
export const strings = (value: unknown): string[] => list(value).filter((v): v is string => typeof v === 'string');

/** The number entries of a list; entries of another type are left out. */
export const numbers = (value: unknown): number[] => list(value).filter((v): v is number => typeof v === 'number');

/** The map entries of a list; entries of another type are left out. */
export const records = (value: unknown): Record<string, unknown>[] =>
  list(value).filter((v): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v));

/** The value as a map, or null for anything else. */
export const record = (value: unknown): Record<string, unknown> | null =>
  typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : null;

/**
 * A timestamp field in milliseconds, or null when it is missing or of another type. With `estimate` a pending server
 * timestamp reads as the phone's estimate (ServerTimestampBehavior.ESTIMATE); without it, as null.
 */
export function millis(s: DocumentSnapshot, field: string, estimate = true): number | null {
  return timeOf(estimate ? s.get(field, { serverTimestamps: 'estimate' }) : s.get(field));
}

/** A Timestamp value in milliseconds, or null for anything else (no stored value has methods). */
export function timeOf(value: unknown): number | null {
  const toMillis = (value as { toMillis?: unknown } | null)?.toMillis;
  return typeof toMillis === 'function' ? (toMillis.call(value) as number) : null;
}

/**
 * Maps each document on its own (after v1, WEB-10): one that fails to map is skipped and logged, and the others still
 * show. A mapper may also return null for a document it leaves out on purpose (an unknown meal slot).
 */
export function mapEach<T>(docs: readonly DocumentSnapshot[], mapper: (d: DocumentSnapshot) => T | null): T[] {
  const result: T[] = [];
  for (const d of docs) {
    try {
      const mapped = mapper(d);
      if (mapped !== null) result.push(mapped);
    } catch (error) {
      skipped(d, error);
    }
  }
  return result;
}

/** A document mapEach skipped: logged in development builds, never shown. */
function skipped(d: DocumentSnapshot, error: unknown) {
  if (import.meta.env.DEV) console.warn(`Skipped a document that doesn't map: ${d.id}`, error);
}
