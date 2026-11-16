// Rows that keep their identity across snapshots (review WEB-13): every snapshot maps every document into new
// objects, so a memoized row would redraw anyway; keeping the previous object when nothing in it changed lets it skip.

/** Whether [a] and [b] hold the same plain data: primitives, arrays and plain objects, compared all the way down. */
export function sameValue(a: unknown, b: unknown): boolean {
  if (Object.is(a, b)) return true;
  if (typeof a !== 'object' || typeof b !== 'object' || a === null || b === null) return false;
  if (Array.isArray(a) !== Array.isArray(b)) return false;
  if (Array.isArray(a)) {
    const other = b as unknown[];
    return a.length === other.length && a.every((value, i) => sameValue(value, other[i]));
  }
  const left = a as Record<string, unknown>;
  const right = b as Record<string, unknown>;
  const keys = Object.keys(left);
  return keys.length === Object.keys(right).length && keys.every((k) => k in right && sameValue(left[k], right[k]));
}

/**
 * [next] with each item replaced by [previous]'s item of the same key when the two hold the same data, so React's
 * `memo` skips rows another phone's change didn't touch.
 */
export function keepUnchanged<T extends { key: string }>(previous: readonly T[], next: readonly T[]): T[] {
  const before = new Map(previous.map((item) => [item.key, item]));
  return next.map((item) => {
    const old = before.get(item.key);
    return old !== undefined && sameValue(old, item) ? old : item;
  });
}
