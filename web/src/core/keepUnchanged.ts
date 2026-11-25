import { useState } from 'react';
import { keepUnchanged } from '../domain/identity';

/**
 * [items] with each one kept from the previous render when its data didn't change (keepUnchanged, by [keyOf]), so
 * memoized rows skip another phone's change to other rows (review WEB-13). React's pattern for information from
 * earlier renders. [keyOf] should keep its identity (a module-level function).
 */
export function useKeepUnchanged<T>(items: T[], keyOf: (item: T) => string): T[] {
  const [kept, setKept] = useState({ source: items, items });
  if (kept.source !== items) {
    const next = { source: items, items: keepUnchanged(kept.items, items, keyOf) };
    setKept(next);
    return next.items;
  }
  return kept.items;
}
