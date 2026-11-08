import { describe, expect, it } from 'vitest';
import { SyncLog } from './delta-sync';

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

  it('works without storage', () => {
    const log = new SyncLog(() => 1_000, null);
    log.markFullSync('households/h1/products');
    log.clear();
    expect(log.lastFullSync('households/h1/products')).toBeNull();
  });
});
