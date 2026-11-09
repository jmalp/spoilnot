import { describe, expect, it } from 'vitest';
import type { QuerySnapshot } from 'firebase/firestore';
import { CURSOR_MARGIN_MS, FULL_SYNC_EVERY_MS } from '../domain/sync';
import { newestReceived, SyncLog } from './delta-sync';

/** localStorage's shape, in memory. */
function memoryStorage(entries: Record<string, string> = {}) {
  const map = new Map(Object.entries(entries));
  return {
    get length() {
      return map.size;
    },
    key: (i: number) => [...map.keys()][i] ?? null,
    getItem: (k: string) => map.get(k) ?? null,
    setItem: (k: string, v: string) => void map.set(k, v),
    removeItem: (k: string) => void map.delete(k),
    keys: () => [...map.keys()],
  };
}

describe('SyncLog', () => {
  it('remembers full reads per collection', () => {
    const storage = memoryStorage();
    const log = new SyncLog(() => 1_000, storage);
    expect(log.lastFullSync('households/h1/products')).toBeNull();
    log.markFullSync('households/h1/products');
    expect(log.lastFullSync('households/h1/products')).toBe(1_000);
    expect(storage.keys()).toEqual(['spoilnot.sync.households/h1/products']);
  });

  it('clearing forgets every collection and keeps the other settings', () => {
    const storage = memoryStorage({
      'spoilnot.themeMode': 'dark',
      'spoilnot.installId': 'browser-1',
      'spoilnot.push.tokenToDelete': '1',
    });
    const log = new SyncLog(() => 1_000, storage);
    log.markFullSync('households/h1/products');
    log.markFullSync('households/h1/stock');
    log.markFullSync('households/h1/groceryLists/l1/items');

    log.clear();

    expect(log.lastFullSync('households/h1/products')).toBeNull();
    expect(storage.keys()).toEqual(['spoilnot.themeMode', 'spoilnot.installId', 'spoilnot.push.tokenToDelete']);
  });

  it('keeps the newest change received next to the full-read record, and the cursor comes from it', () => {
    const storage = memoryStorage();
    let now = 10_000_000;
    const log = new SyncLog(() => now, storage);
    const items = 'households/h1/groceryLists/l1/items';
    expect(log.cursor(items)).toBeNull();
    log.markFullSync(items);
    expect(log.cursor(items)).toBeNull(); // a full read that brought nothing reads in full again
    log.markSeen(items, 9_000_000);
    log.markSeen(items, 8_000_000); // never moves back
    expect(log.newestSeen(items)).toBe(9_000_000);
    expect(log.cursor(items)).toBe(9_000_000 - CURSOR_MARGIN_MS);
    expect(storage.keys()).toEqual([`spoilnot.sync.${items}`, `spoilnot.sync.seen:${items}`]);
    expect(log.cursor('households/h1/meals?date=2026-11-09..2026-11-15')).toBeNull();
    now += FULL_SYNC_EVERY_MS;
    expect(log.cursor(items)).toBeNull(); // the daily full read
    log.clear();
    expect(log.newestSeen(items)).toBeNull();
    expect(storage.keys()).toEqual([]);
  });

  it('takes a shorter margin for tests', () => {
    const log = new SyncLog(() => 10_000_000, memoryStorage(), 2_000);
    log.markFullSync('households/h1/stock');
    log.markSeen('households/h1/stock', 9_000_000);
    expect(log.cursor('households/h1/stock')).toBe(8_998_000);
  });

  it('works without storage', () => {
    const log = new SyncLog(() => 1_000, null);
    log.markFullSync('households/h1/products');
    log.clear();
    expect(log.lastFullSync('households/h1/products')).toBeNull();
  });
});

/** A query snapshot's shape: documents with an updatedAt (millis, or null while pending) and pending writes. */
function snapshot(fromCache: boolean, docs: { at: number | null; pending?: boolean }[]): QuerySnapshot {
  return {
    metadata: { fromCache, hasPendingWrites: docs.some((d) => d.pending) },
    docs: docs.map((d) => ({
      metadata: { fromCache, hasPendingWrites: d.pending ?? false },
      get: () => (d.at === null ? null : { toMillis: () => d.at }),
    })),
  } as unknown as QuerySnapshot;
}

describe('newestReceived', () => {
  it('takes the newest change the server sent', () => {
    expect(newestReceived(snapshot(false, [{ at: 3 }, { at: 7 }, { at: null }]), 'updatedAt')).toBe(7);
  });

  it('learns nothing from the cache or from the phone’s unsent writes', () => {
    expect(newestReceived(snapshot(true, [{ at: 7 }]), 'updatedAt')).toBeNull();
    expect(newestReceived(snapshot(false, [{ at: 3 }, { at: 9, pending: true }]), 'updatedAt')).toBe(3);
    expect(newestReceived(snapshot(false, []), 'updatedAt')).toBeNull();
  });
});
