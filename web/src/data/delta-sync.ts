// Changes-only listeners (DESIGN 9.8; core/firebase/DeltaSync in the app). See domain/sync.ts for the cursor.
import {
  deleteField,
  getDocsFromCache,
  limit as limitTo,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  Timestamp,
  where,
  type CollectionReference,
  type DocumentSnapshot,
  type Firestore,
  type Query,
  type QuerySnapshot,
} from 'firebase/firestore';
import {
  BehaviorSubject,
  catchError,
  combineLatest,
  concat,
  defer,
  distinctUntilChanged,
  map,
  Observable,
  of,
  switchMap,
  tap,
  throwError,
  timer,
} from 'rxjs';
import { isAppError } from '../core/errors';
import { CURSOR_MARGIN_MS, cursor } from '../domain/sync';
import type { Actor } from '../domain/write';
import { querySnapshots, retryAfterOwnWrites, toAppError } from './firestore-rx';

const UPDATED_AT = 'updatedAt';

/** The audit fields every mutable household document carries (DESIGN 8.1). */
export const Stamps = {
  updated: (actor: Actor) => ({ updatedAt: serverTimestamp(), updatedBy: actor.uid }),
  created: (actor: Actor) => ({ createdAt: serverTimestamp(), createdBy: actor.uid }),
};

/** Deleted household documents: the document stays, marked with deletedAt, until the daily job (2 days). */
/**
 * The fields of `after` whose values differ from `before`: what an edit writes, so it never puts back a field another
 * member changed meanwhile (after v1, DESIGN 9.4).
 */
export function changedFields<T extends Record<string, unknown>>(before: T, after: T): Partial<T> {
  const out: Partial<T> = {};
  for (const key of Object.keys(after) as (keyof T)[]) {
    if (JSON.stringify(after[key]) !== JSON.stringify(before[key])) out[key] = after[key];
  }
  return out;
}

export const Tombstones = {
  FIELD: 'deletedAt',
  fields: (actor: Actor) => ({ deletedAt: serverTimestamp(), ...Stamps.updated(actor) }),
  /**
   * Undo of a deletion, merged into the tombstone: removes deletedAt and stamps the write, and changes nothing else
   * (after v1, DESIGN 8.6). The tombstone kept every field, so the document comes back as it is on the server, with
   * fields this app doesn't know, its createdBy and any change another member made meanwhile.
   */
  restore: (actor: Actor) => ({ deletedAt: deleteField(), ...Stamps.updated(actor) }),
  /**
   * Deleted means deletedAt holds a time, the phone's estimate included: until the server confirms an offline delete,
   * the default read gives null and the document would stay on screen (DESIGN 9.8). Undo removes the field (restore).
   */
  is: (snapshot: DocumentSnapshot) => snapshot.get('deletedAt', { serverTimestamps: 'estimate' }) != null,
};

const SYNC_KEY_PREFIX = 'spoilnot.sync.';
/** Next to each full-read record: the newest change a server snapshot brought (the cursor, less the margin). */
const SEEN_KEY_PREFIX = `${SYNC_KEY_PREFIX}seen:`;

/**
 * Per collection (and meal window) on this phone: when it was last read in full, and the newest change its server
 * listener received, where the next changes-only read starts (local, not household data). Its keys name the
 * collections, so household and list ids, and it is cleared with the local data (DESIGN 12.2, control 6). [marginMs]
 * is the cursor margin; tests shorten it.
 */
export class SyncLog {
  /** Bumped whenever a full read is recorded or the log is cleared. */
  private readonly changes = new BehaviorSubject(0);

  constructor(
    private readonly now: () => number = Date.now,
    private readonly storage: Pick<
      Storage,
      'getItem' | 'setItem' | 'removeItem' | 'key' | 'length'
    > | null = safeStorage(),
    private readonly marginMs: number = CURSOR_MARGIN_MS,
  ) {}
  lastFullSync(key: string): number | null {
    return this.read(`${SYNC_KEY_PREFIX}${key}`);
  }
  markFullSync(key: string): void {
    this.write(`${SYNC_KEY_PREFIX}${key}`, this.now());
    this.changes.next(this.changes.value + 1);
  }
  /**
   * [lastFullSync] for [key], now and whenever a full read is recorded or the log is cleared (after v1, for the
   * restock engine's cache-only listener, DESIGN 9.6).
   */
  fullSyncs$(key: string): Observable<number | null> {
    return this.changes.pipe(
      map(() => this.lastFullSync(key)),
      distinctUntilChanged(),
    );
  }
  /** The newest change time a server snapshot of [key] brought (DESIGN 9.8), or null before the first one. */
  newestSeen(key: string): number | null {
    return this.read(`${SEEN_KEY_PREFIX}${key}`);
  }
  /** Records [at] for [key] if it is newer than what is on record; it never moves back. */
  markSeen(key: string, at: number): void {
    if (at > (this.newestSeen(key) ?? 0)) this.write(`${SEEN_KEY_PREFIX}${key}`, at);
  }
  /** Where the server listener for [key] starts; null reads everything (domain/sync.ts). */
  cursor(key: string): number | null {
    return cursor(this.newestSeen(key), this.lastFullSync(key), this.now(), this.marginMs);
  }
  /** Forgets every collection; the next read of each is a full one. Other keys (display settings) stay. */
  clear(): void {
    const storage = this.storage;
    if (!storage) return;
    try {
      const keys: string[] = [];
      for (let i = 0; i < storage.length; i++) {
        const key = storage.key(i);
        if (key?.startsWith(SYNC_KEY_PREFIX)) keys.push(key);
      }
      keys.forEach((key) => storage.removeItem(key));
    } catch {
      // Storage refused: nothing was kept either.
    }
    this.changes.next(this.changes.value + 1);
  }
  current(): number {
    return this.now();
  }
  private read(key: string): number | null {
    try {
      const value = Number(this.storage?.getItem(key));
      return value > 0 ? value : null;
    } catch {
      return null;
    }
  }
  private write(key: string, value: number): void {
    try {
      this.storage?.setItem(key, String(value));
    } catch {
      // Storage full or refused: the next open reads in full, which is only slower.
    }
  }
}

/** The browser tests' shorter cursor margin, from localStorage; development builds only (Vite drops it otherwise). */
export const TEST_CURSOR_MARGIN_KEY = 'spoilnot.test.cursorMarginMs';

export function cursorMarginMs(): number {
  if (!import.meta.env.DEV) return CURSOR_MARGIN_MS;
  const forced = Number(safeStorage()?.getItem(TEST_CURSOR_MARGIN_KEY));
  return forced > 0 ? forced : CURSOR_MARGIN_MS;
}

function safeStorage(): Storage | null {
  try {
    return localStorage;
  } catch {
    return null;
  }
}

/** The live documents of a collection (tombstones left out) and whether the server has confirmed them yet. */
export type LiveDocuments = { documents: DocumentSnapshot[]; fromCache: boolean };

/**
 * The newest [field] time in a server listener's [snapshot], or null when there is nothing to learn from it: a
 * snapshot from the cache isn't what the server sent, and a document with unsent writes shows the phone's copy.
 */
export function newestReceived(snapshot: QuerySnapshot, field: string): number | null {
  if (snapshot.metadata.fromCache) return null;
  let newest: number | null = null;
  for (const d of snapshot.docs) {
    if (d.metadata.hasPendingWrites) continue;
    const at = (d.get(field) as Timestamp | null)?.toMillis?.() ?? null;
    if (at !== null && (newest === null || at > newest)) newest = at;
  }
  return newest;
}

/** What a server listener's [snapshot] for [key] tells the log: a full read, and the newest change it brought. */
function recordServerSnapshot(log: SyncLog, key: string, snapshot: QuerySnapshot, field: string, fullRead: boolean) {
  if (snapshot.metadata.fromCache) return;
  const newest = newestReceived(snapshot, field);
  if (newest !== null) log.markSeen(key, newest);
  if (fullRead) log.markFullSync(key);
}

/** The pause before a refused server listener listens again: 1, 2, 4 … 32 s (as the session). */
export const retryDelayMs = (attempt: number): number => 1_000 * 2 ** Math.min(Math.max(attempt, 0), 5);

/**
 * The server listener's "from the cache" flag, kept alive when the server refuses it for the quota or can't be
 * reached (after v1, WEB-04): it then reports the cache (so the screen keeps its list and says "Offline") and listens
 * again after 1, 2, 4 … 32 s. A refused listen reads nothing, so the retries cost no quota. Other errors (a permission
 * refusal, a bad document) still end the stream.
 */
export function keepCacheWhileUnreachable() {
  return (source: Observable<boolean>): Observable<boolean> => {
    const attempt = (n: number): Observable<boolean> =>
      source.pipe(
        catchError((error: unknown) => {
          if (!isAppError(error, 'quotaExceeded') && !isAppError(error, 'offline')) return throwError(() => error);
          return concat(of(true), timer(retryDelayMs(n)).pipe(switchMap(() => attempt(n + 1))));
        }),
      );
    return attempt(0);
  };
}

/** Nothing in the cache for this query (or the cache can't be read): read it in full, whatever the log says. */
async function cachedEmpty(scope: Query): Promise<boolean> {
  const cached = await getDocsFromCache(scope).catch(() => null);
  return cached === null || cached.empty;
}

/** A listener on the local cache only: free, and updated by local writes and by every other listener. */
function cacheSnapshots(scope: Query): Observable<QuerySnapshot> {
  return new Observable((subscriber) =>
    onSnapshot(
      scope,
      { includeMetadataChanges: true, source: 'cache' },
      (snapshot) => subscriber.next(snapshot),
      (error) => subscriber.error(toAppError(error)),
    ),
  );
}

/**
 * The live documents of [scope] in the local cache only, tombstones left out (after v1, DESIGN 9.6): free, with no
 * server listener, so it reads nothing when the app comes back after a long time.
 */
export function cachedLiveDocuments(scope: Query): Observable<DocumentSnapshot[]> {
  return cacheSnapshots(scope).pipe(map((snapshot) => snapshot.docs.filter((d) => !Tombstones.is(d))));
}

/**
 * DeltaSync for a collection, or part of one ([scope], with its own full-read record and cursor [key]): the documents
 * [scope] shows, with changes read for the whole collection.
 */
export function liveDocuments(
  db: Firestore,
  collection: CollectionReference,
  log: SyncLog,
  scope: Query = collection,
  key: string = collection.path,
): Observable<LiveDocuments> {
  return defer(async () => ((await cachedEmpty(scope)) ? null : log.cursor(key))).pipe(
    switchMap((since) => {
      const server = since === null ? scope : query(collection, where(UPDATED_AT, '>', Timestamp.fromMillis(since)));
      const remote = querySnapshots(server, true).pipe(
        retryAfterOwnWrites(db),
        tap((s) => recordServerSnapshot(log, key, s, UPDATED_AT, since === null)),
        map((s) => s.metadata.fromCache),
        keepCacheWhileUnreachable(),
      );
      return combineLatest([cacheSnapshots(scope), remote]).pipe(
        map(([local, fromCache]) => ({ documents: local.docs.filter((d) => !Tombstones.is(d)), fromCache })),
      );
    }),
  );
}

/**
 * The newest [limit] documents of an append-only collection by [field] (liveNewest in the app), such as Recent
 * activity: the cache listener shows the newest ones the phone has, and the server listener reads only documents newer
 * than the newest one it received before (less the cursor margin), or the newest [limit] on the daily full read. Removals that
 * happen while a document is in the server listener's result (Undo) show at once; others by the next full read.
 */
export function liveNewest(
  db: Firestore,
  collection: CollectionReference,
  log: SyncLog,
  field: string,
  limit: number,
): Observable<LiveDocuments> {
  const newestQuery = query(collection, orderBy(field, 'desc'), limitTo(limit));
  const key = collection.path;
  return defer(async () => ((await cachedEmpty(newestQuery)) ? null : log.cursor(key))).pipe(
    switchMap((since) => {
      const server = since === null ? newestQuery : query(collection, where(field, '>', Timestamp.fromMillis(since)));
      const remote = querySnapshots(server, true).pipe(
        retryAfterOwnWrites(db),
        tap((s) => recordServerSnapshot(log, key, s, field, since === null)),
        map((s) => s.metadata.fromCache),
        keepCacheWhileUnreachable(),
      );
      return combineLatest([cacheSnapshots(newestQuery), remote]).pipe(
        map(([local, fromCache]) => ({ documents: local.docs, fromCache })),
      );
    }),
  );
}
