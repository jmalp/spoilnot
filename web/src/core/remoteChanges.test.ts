// Ported from RemoteChangeTrackerTest's cases.
import { describe, expect, it } from 'vitest';
import { RemoteChangeTracker } from './remoteChanges';

type Row = { id: string; value: number; pending: boolean };
const row = (id: string, value: number, pending = false): Row => ({ id, value, pending });
const tracker = () =>
  new RemoteChangeTracker<Row>(
    (r) => r.id,
    (r) => r.pending,
    (r) => r.value,
  );

describe('RemoteChangeTracker', () => {
  it('nothing is highlighted on the first load', () => {
    expect(tracker().update([row('a', 1), row('b', 2)])).toEqual(new Set());
  });

  it("another phone's new and changed rows are highlighted", () => {
    const t = tracker();
    t.update([row('a', 1)]);
    expect(t.update([row('a', 2), row('b', 1)])).toEqual(new Set(['a', 'b']));
    expect(t.update([row('a', 2), row('b', 1)])).toEqual(new Set());
  });

  it("this phone's own writes are not: they show up with pending writes first", () => {
    const t = tracker();
    t.update([row('a', 1)]);
    expect(t.update([row('a', 2, true), row('b', 1, true)])).toEqual(new Set());
    // The server's confirmation of the same data is not a change either.
    expect(t.update([row('a', 2), row('b', 1)])).toEqual(new Set());
  });

  it("a new scope's first load is not highlighted, as another grocery list's rows aren't new", () => {
    const t = tracker();
    t.update([row('a', 1)], 'list-1');
    // The old list's rows under the new list for a moment, then the new list's own rows.
    expect(t.update([row('a', 1)], 'list-1')).toEqual(new Set());
    expect(t.update([row('b', 1), row('c', 1)], 'list-2')).toEqual(new Set());
    expect(t.update([row('b', 1), row('c', 2), row('d', 1)], 'list-2')).toEqual(new Set(['c', 'd']));
    // And back: the first list again is a first load too.
    expect(t.update([row('a', 5)], 'list-1')).toEqual(new Set());
  });
});
