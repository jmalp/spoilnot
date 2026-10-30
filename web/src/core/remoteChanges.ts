// Rows another phone changed get a short highlight (DESIGN 6.4), ported from core/ui/RemoteChanges.
import { useEffect, useRef, useState } from 'react';

/** How long a row changed on another phone stays highlighted. */
export const REMOTE_HIGHLIGHT_MS = 1_500;

/**
 * Tells which rows another phone changed. This phone's own writes always show up first with pending writes, so a row
 * whose data changed while it had none came from someone else. Nothing is highlighted on the first load.
 */
export class RemoteChangeTracker<T> {
  private previous: Map<string, [string, boolean]> | null = null;

  constructor(
    private readonly id: (item: T) => string,
    private readonly pending: (item: T) => boolean,
    private readonly content: (item: T) => unknown,
  ) {}

  /** The ids changed remotely since the last call. */
  update(items: T[]): Set<string> {
    const before = this.previous;
    this.previous = new Map(items.map((i) => [this.id(i), [JSON.stringify(this.content(i)), this.pending(i)]]));
    if (before === null) return new Set();
    return new Set(
      items
        .filter((item) => {
          if (this.pending(item)) return false;
          const old = before.get(this.id(item));
          return old === undefined || (!old[1] && old[0] !== JSON.stringify(this.content(item)));
        })
        .map(this.id),
    );
  }
}

/** Ids to highlight now; each one clears itself after [REMOTE_HIGHLIGHT_MS]. */
export function useRemoteChanges<T>(
  items: T[],
  id: (item: T) => string,
  pending: (item: T) => boolean,
  content: (item: T) => unknown,
): Set<string> {
  const [tracker] = useState(() => new RemoteChangeTracker(id, pending, content));
  const [active, setActive] = useState<ReadonlySet<string>>(new Set());
  const timers = useRef(new Map<string, number>());
  useEffect(() => {
    const changed = tracker.update(items);
    if (changed.size === 0) return;
    const show = window.setTimeout(() => setActive((now) => new Set([...now, ...changed])));
    for (const key of changed) {
      window.clearTimeout(timers.current.get(key));
      timers.current.set(
        key,
        window.setTimeout(() => {
          timers.current.delete(key);
          setActive((now) => new Set([...now].filter((k) => k !== key)));
        }, REMOTE_HIGHLIGHT_MS),
      );
    }
    return () => window.clearTimeout(show);
  }, [items, tracker]);
  useEffect(() => {
    const pendingTimers = timers.current;
    return () => pendingTimers.forEach((t) => window.clearTimeout(t));
  }, []);
  return active as Set<string>;
}
