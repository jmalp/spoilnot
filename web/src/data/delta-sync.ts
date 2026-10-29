// Changes-only listeners (DESIGN 9.8; core/firebase/DeltaSync in the app). See domain/sync.ts for the cursor.
import {
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
import { combineLatest, defer, map, Observable, switchMap, tap } from 'rxjs';
import { cursor } from '../domain/sync';
import type { Actor } from '../domain/write';
import { querySnapshots, retryAfterOwnWrites, toAppError } from './firestore-rx';

const UPDATED_AT = 'updatedAt';

/** The audit fields every mutable household document carries (DESIGN 8.1). */
export const Stamps = {
  updated: (actor: Actor) => ({ updatedAt: serverTimestamp(), updatedBy: actor.uid }),
  created: (actor: Actor) => ({ createdAt: serverTimestamp(), createdBy: actor.uid }),
};

/** Deleted household documents: the document stays, marked with deletedAt, until the daily job (2 days). */
export const Tombstones = {
  FIELD: 'deletedAt',
  fields: (actor: Actor) => ({ deletedAt: serverTimestamp(), ...Stamps.updated(actor) }),
  is: (snapshot: DocumentSnapshot) => snapshot.get('deletedAt') != null,
};

/** When each collection was last read in full on this phone (local, not household data). */
export class SyncLog {
  constructor(
    private readonly now: () => number = Date.now,
    private readonly storage: Pick<Storage, 'getItem' | 'setItem'> | null = safeStorage(),
  ) {}
  lastFullSync(key: string): number | null {
    const value = Number(this.storage?.getItem(`spoilnot.sync.${key}`));
    return value > 0 ? value : null;
  }
  markFullSync(key: string): void {
    this.storage?.setItem(`spoilnot.sync.${key}`, String(this.now()));
  }
  current(): number {
    return this.now();
  }
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
 * DeltaSync for a collection, or part of one ([scope], with its own full-read record [key]): the documents [scope]
 * shows, with changes read for the whole collection.
 */
export function liveDocuments(
  db: Firestore,
  collection: CollectionReference,
  log: SyncLog,
  scope: Query = collection,
  key: string = collection.path,
): Observable<LiveDocuments> {
  return defer(async () => {
    const cached = await getDocsFromCache(scope).catch(() => null);
    const newest = cached?.docs.reduce<number | null>((max, d) => {
      const at = (d.get(UPDATED_AT) as Timestamp | null)?.toMillis?.() ?? null;
      return at !== null && (max === null || at > max) ? at : max;
    }, null);
    return cursor(newest ?? null, log.lastFullSync(key), log.current());
  }).pipe(
    switchMap((since) => {
      const server = since === null ? scope : query(collection, where(UPDATED_AT, '>', Timestamp.fromMillis(since)));
      const remote = querySnapshots(server, true).pipe(
        retryAfterOwnWrites(db),
        tap((s) => {
          if (since === null && !s.metadata.fromCache) log.markFullSync(key);
        }),
      );
      return combineLatest([cacheSnapshots(scope), remote]).pipe(
        map(([local, server]) => ({
          documents: local.docs.filter((d) => !Tombstones.is(d)),
          fromCache: server.metadata.fromCache,
        })),
      );
    }),
  );
}

/**
 * The newest [limit] documents of an append-only collection by [field] (liveNewest in the app), such as Recent
 * activity: the cache listener shows the newest ones the phone has, and the server listener reads only documents newer
 * than the newest cached one (less the cursor margin), or the newest [limit] on the daily full read. Removals that
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
  return defer(async () => {
    const cached = await getDocsFromCache(newestQuery).catch(() => null);
    const newest = cached?.docs
      .map((d) => (d.get(field) as Timestamp | null)?.toMillis?.() ?? null)
      .find((at) => at !== null);
    return cursor(newest ?? null, log.lastFullSync(collection.path), log.current());
  }).pipe(
    switchMap((since) => {
      const server = since === null ? newestQuery : query(collection, where(field, '>', Timestamp.fromMillis(since)));
      const remote = querySnapshots(server, true).pipe(
        retryAfterOwnWrites(db),
        tap((s) => {
          if (since === null && !s.metadata.fromCache) log.markFullSync(collection.path);
        }),
      );
      return combineLatest([cacheSnapshots(newestQuery), remote]).pipe(
        map(([local, server]) => ({ documents: local.docs, fromCache: server.metadata.fromCache })),
      );
    }),
  );
}
