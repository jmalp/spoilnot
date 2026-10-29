// Cheaper household listeners (DESIGN 9.8), ported from DeltaSync: a free cache listener on the whole collection,
// and a server listener on only the documents changed since the newest cached one, with a full read at least daily.

/** A full read at least this often per collection and phone. */
export const FULL_SYNC_EVERY_MS = 20 * 3_600_000;
/** Reread a little before the newest cached change, in case two writes landed close together. */
export const CURSOR_MARGIN_MS = 5 * 60_000;

/**
 * Where the server listener starts (milliseconds): null means read everything (no cached change yet, no full read on
 * record, the last one older than FULL_SYNC_EVERY, or the clock moved back).
 */
export function cursor(newestCached: number | null, lastFullSync: number | null, now: number): number | null {
  if (newestCached === null || lastFullSync === null) return null;
  if (now - lastFullSync >= FULL_SYNC_EVERY_MS) return null;
  if (lastFullSync > now) return null;
  return newestCached - CURSOR_MARGIN_MS;
}
