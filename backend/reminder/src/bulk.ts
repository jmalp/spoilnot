// Bulk writes whose failures are counted, never thrown or printed (DESIGN 13.4). A failed BulkWriter write rejects
// with an error that holds the document reference, and through it the Firestore client and the service account key;
// left unhandled, Node would print all of it into the public workflow log.
import { GrpcStatus, type DocumentData, type DocumentReference, type Firestore } from 'firebase-admin/firestore';

/** BulkWriter's own retry rule (firebase-admin 14), kept explicitly: these codes, and INTERNAL on deletes. */
const RETRY = new Set<number>([GrpcStatus.ABORTED, GrpcStatus.UNAVAILABLE, GrpcStatus.RESOURCE_EXHAUSTED]);
const MAX_ATTEMPTS = 10;

export interface BulkWrites {
  set(ref: DocumentReference, data: DocumentData): void;
  delete(ref: DocumentReference): void;
  /** Waits for every write; resolves with the number that failed after retries. */
  close(): Promise<number>;
}

export function bulkWrites(db: Firestore): BulkWrites {
  const writer = db.bulkWriter();
  writer.onWriteError((error) =>
    (RETRY.has(error.code) || (error.operationType === 'delete' && error.code === GrpcStatus.INTERNAL)) &&
    error.failedAttempts < MAX_ATTEMPTS);
  let failed = 0;
  const settled: Array<Promise<void>> = [];
  const track = (write: Promise<unknown>) => {
    settled.push(write.then(() => {}, () => { failed++; }));
  };
  return {
    set: (ref, data) => track(writer.set(ref, data)),
    delete: (ref) => track(writer.delete(ref)),
    async close() {
      await writer.close();
      await Promise.all(settled);
      return failed;
    },
  };
}
