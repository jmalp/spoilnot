// A clean-up the server refused isn't retried in a loop (after v1, review REL-11; EmptyBatchCleanupsTest in the app).
import { describe, expect, it } from 'vitest';
import { EmptyBatchCleanups } from './cleanups';

describe('EmptyBatchCleanups', () => {
  it("doesn't write a batch again while its clean-up waits for the server", () => {
    const cleanups = new EmptyBatchCleanups();
    expect(cleanups.toTry(['a', 'b'])).toEqual(['a', 'b']);
    expect(cleanups.toTry(['a', 'b', 'c'])).toEqual(['c']);
  });

  it('never tries a refused clean-up again, and tries an accepted one again', () => {
    const cleanups = new EmptyBatchCleanups();
    cleanups.toTry(['neg', 'zero']);
    cleanups.answered(['neg'], true);
    cleanups.answered(['zero'], false);
    expect(cleanups.toTry(['neg', 'zero'])).toEqual(['zero']);
  });
});
