// Cheaper household listeners (DESIGN 9.8), ported from DeltaSync: a free cache listener on the whole collection,
// and a server listener on only the documents changed since the cursor, with a full read at least daily. The cursor is
// the newest change this phone's server listener received (SyncLog), never the newest one in the cache: the phone's
// own writes put fresh server times there without anything having read the other members' changes (WEB-16).

/** A full read at least this often per collection and phone. */
export const FULL_SYNC_EVERY_MS = 20 * 3_600_000;
/**
 * Reread a little before the newest change received: a snapshot can show the phone's own confirmed write before the
 * server has sent another member's slightly older one.
 */
export const CURSOR_MARGIN_MS = 5 * 60_000;

/**
 * Where the server listener starts (milliseconds): null means read everything (no change received yet, no full read
 * on record, the last one older than FULL_SYNC_EVERY, or the clock moved back).
 */
export function cursor(
  newestSeen: number | null,
  lastFullSync: number | null,
  now: number,
  marginMs: number = CURSOR_MARGIN_MS,
): number | null {
  if (newestSeen === null || lastFullSync === null) return null;
  if (now - lastFullSync >= FULL_SYNC_EVERY_MS) return null;
  if (lastFullSync > now) return null;
  return newestSeen - marginMs;
}
