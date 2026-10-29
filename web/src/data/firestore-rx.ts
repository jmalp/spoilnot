// Firestore helpers for src/data only (core/firebase/FirestoreFlows in the app): listeners as Observables, Firebase
// errors as AppError, and batches that may stay queued offline or must reach the server.
import { FirebaseError } from 'firebase/app';
import {
  onSnapshot,
  waitForPendingWrites,
  type DocumentReference,
  type DocumentSnapshot,
  type Firestore,
  type Query,
  type QuerySnapshot,
  type WriteBatch,
} from 'firebase/firestore';
import { from, Observable, race, retry, switchMap, timer } from 'rxjs';
import { AppError, isAppError } from '../core/errors';

const QUEUED_WRITE_WAIT_MS = 3_000;
const PENDING_WRITES_WAIT_MS = 5_000;
const RETRY_DELAY_MS = 250;
const CONFIRMED_WRITE_TIMEOUT_MS = 15_000;

export function toAppError(error: unknown): AppError {
  if (error instanceof AppError) return error;
  if (error instanceof FirebaseError) {
    switch (error.code.replace(/^firestore\//, '')) {
      case 'permission-denied':
      case 'unauthenticated':
        return new AppError('permissionDenied', { cause: error });
      case 'resource-exhausted':
        return new AppError('quotaExceeded', { cause: error });
      case 'unavailable':
      case 'deadline-exceeded':
        return new AppError('offline', { cause: error });
      case 'not-found':
        return new AppError('notFound', { cause: error });
      case 'already-exists':
      case 'aborted':
      case 'failed-precondition':
        return new AppError('conflict', { cause: error });
    }
  }
  return new AppError('unknown', { cause: error });
}

export function docSnapshots(ref: DocumentReference, includeMetadataChanges = false): Observable<DocumentSnapshot> {
  return new Observable((subscriber) =>
    onSnapshot(
      ref,
      { includeMetadataChanges },
      (snapshot) => subscriber.next(snapshot),
      (error) => subscriber.error(toAppError(error)),
    ),
  );
}

export function querySnapshots(query: Query, includeMetadataChanges = false): Observable<QuerySnapshot> {
  return new Observable((subscriber) =>
    onSnapshot(
      query,
      { includeMetadataChanges },
      (snapshot) => subscriber.next(snapshot),
      (error) => subscriber.error(toAppError(error)),
    ),
  );
}

/**
 * Retries a household listener the server refused because this device's own writes have not landed yet: right
 * after creating or joining a household the listener can reach the server before the membership write. A second
 * refusal after our pending writes are acknowledged is real.
 */
export function retryAfterOwnWrites<T>(db: Firestore, attempts = 2) {
  return (source: Observable<T>) =>
    source.pipe(
      retry({
        count: attempts,
        delay: (error: unknown, attempt) => {
          if (!isAppError(error, 'permissionDenied')) throw error;
          return race(from(waitForPendingWrites(db)), timer(PENDING_WRITES_WAIT_MS)).pipe(
            switchMap(() => timer(RETRY_DELAY_MS * attempt)),
          );
        },
      }),
    );
}

/** Awaits a Firebase call, mapping its failures to AppError. */
export async function mapped<T>(work: Promise<T>): Promise<T> {
  try {
    return await work;
  } catch (error) {
    throw toAppError(error);
  }
}

/**
 * Commits a batch that may stay queued offline (DESIGN 9.5): returns once the server accepts it, or after a short
 * wait while it remains queued in the local cache. Rule rejections that arrive in time are thrown.
 */
export async function commitQueued(batch: WriteBatch): Promise<void> {
  const commit = mapped(batch.commit());
  // A rejection after the wait has nowhere to go; the listeners show the server's state.
  commit.catch(() => undefined);
  await Promise.race([commit, new Promise((resolve) => setTimeout(resolve, QUEUED_WRITE_WAIT_MS))]);
}

/** Commits a batch that must reach the server, such as leaving a household before the cache is cleared. */
export async function commitConfirmed(batch: WriteBatch): Promise<void> {
  let timeout: ReturnType<typeof setTimeout> | undefined;
  try {
    await Promise.race([
      mapped(batch.commit()),
      new Promise((_, reject) => {
        timeout = setTimeout(() => reject(new AppError('offline')), CONFIRMED_WRITE_TIMEOUT_MS);
      }),
    ]);
  } finally {
    clearTimeout(timeout);
  }
}
