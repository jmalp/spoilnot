// Ported from WriteBatchesTest.
import { describe, expect, it } from 'vitest';
import { planBatches, type CounterChange, type HouseholdChange } from './write';

const plain = (id: number): HouseholdChange & { id: number } => ({ type: 'test.plain', id });
const count = (counter: string, delta: number): CounterChange => ({ type: 'test.count', counter, delta });

describe('planBatches', () => {
  it('chunks changes without counts in order at the limit', () => {
    const changes = Array.from({ length: 9 }, (_, i) => plain(i + 1));
    expect(planBatches(changes, 4)).toEqual([changes.slice(0, 4), changes.slice(4, 8), changes.slice(8)]);
    expect(planBatches([])).toEqual([]);
  });

  it('sends a count of three as one step with its changes and two batches of one step after', () => {
    expect(planBatches([plain(1), plain(2), count('2026-11', 3)])).toEqual([
      [plain(1), plain(2), count('2026-11', 1)],
      [count('2026-11', 1)],
      [count('2026-11', 1)],
    ]);
  });

  it('takes an undo back one step at a time', () => {
    expect(planBatches([plain(1), count('m', -2)])).toEqual([[plain(1), count('m', -1)], [count('m', -1)]]);
  });

  it('never puts two counts of one document in a batch; counts of different documents may share one', () => {
    const plan = planBatches([plain(1), count('m', 1), plain(2), count('m', 1), count('n', 1), count('m', 1)]);
    expect(plan).toEqual([[plain(1), count('m', 1), plain(2), count('n', 1)], [count('m', 1)], [count('m', 1)]]);
    for (const batch of plan) {
      const counters = batch.filter((c): c is CounterChange => 'counter' in c).map((c) => c.counter);
      expect(new Set(counters).size).toBe(counters.length);
    }
  });

  it('keeps later steps within the limit and writes nothing for a zero count', () => {
    const plan = planBatches([count('a', 2), count('b', 2), count('c', 2), count('z', 0)], 2);
    expect(plan).toEqual([
      [count('a', 1), count('b', 1)],
      [count('c', 1), count('a', 1)],
      [count('b', 1), count('c', 1)],
    ]);
  });
});
