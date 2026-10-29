// Recent activity (feature/home/data in the app): households/{hid}/activity, read changes-only (DESIGN 9.8).
import { collection, type DocumentSnapshot, type Timestamp } from 'firebase/firestore';
import { distinctUntilChanged, map, switchMap, type Observable } from 'rxjs';
import { activityTypeOf, ActivityFeed, type ActivityEvent, type ActivityRepository } from '../domain/home/models';
import { discardReasonOf } from '../domain/pantry/models';
import { liveNewest, type SyncLog } from './delta-sync';
import type { FirebaseSdk } from './firebase';
import { activity } from './pantry';

const num = (value: unknown): number | null => (typeof value === 'number' ? value : null);
const text = (value: unknown): string | null => (typeof value === 'string' ? value : null);

function toEvent(s: DocumentSnapshot): ActivityEvent | null {
  const type = activityTypeOf(s.get('type'));
  if (type === null) return null;
  const params = (s.get('params') as Record<string, unknown> | null) ?? {};
  const count = num(params.count);
  return {
    id: s.id,
    type,
    actorUid: text(s.get('actorUid')) ?? '',
    actorName: text(s.get('actorName')) ?? '',
    name: text(params.name),
    quantity: num(params.quantity),
    reason: discardReasonOf(params.reason),
    listName: text(params.listName),
    count: count === null ? null : Math.trunc(count),
    createdAt: (s.get('createdAt', { serverTimestamps: 'estimate' }) as Timestamp | null)?.toMillis?.() ?? null,
  };
}

export class FirestoreActivityRepository implements ActivityRepository {
  constructor(
    private readonly sdk: FirebaseSdk,
    private readonly log: SyncLog,
  ) {}

  // Changes-only (after v1, DESIGN 9.8): new events cost one read each instead of the newest ten on every open.
  // Single-field order and range use the automatic indexes (DESIGN 8.4).
  observeRecent(householdId: string, limit: number): Observable<ActivityEvent[]> {
    return this.sdk.instance$.pipe(
      switchMap((db) => liveNewest(db, collection(db, activity(householdId)), this.log, 'createdAt', limit)),
      map((live) => ActivityFeed.sinceReset(live.documents.map(toEvent).filter((e): e is ActivityEvent => e !== null))),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
    );
  }
}
