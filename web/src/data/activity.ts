// Recent activity (feature/home/data in the app): households/{hid}/activity, read changes-only (DESIGN 9.8).
import { collection, type DocumentSnapshot } from 'firebase/firestore';
import { distinctUntilChanged, map, switchMap, type Observable } from 'rxjs';
import { activityTypeOf, ActivityFeed, type ActivityEvent, type ActivityRepository } from '../domain/home/models';
import { discardReasonOf } from '../domain/pantry/models';
import { liveNewest, type SyncLog } from './delta-sync';
import type { FirebaseSdk } from './firebase';
import { activity } from './pantry';
import { capOrNull, capTo, ReadCaps } from '../domain/caps';
import { mapEach, millis, num, record, str as text } from './fields';

export function toEvent(s: DocumentSnapshot): ActivityEvent | null {
  const type = activityTypeOf(s.get('type'));
  if (type === null) return null;
  const params = record(s.get('params')) ?? {};
  const count = num(params.count);
  return {
    id: s.id,
    type,
    actorUid: text(s.get('actorUid')) ?? '',
    actorName: capTo(text(s.get('actorName')) ?? '', ReadCaps.PERSON),
    // A product, list item or recipe title.
    name: capOrNull(text(params.name), ReadCaps.RECIPE_TITLE),
    quantity: num(params.quantity),
    reason: discardReasonOf(params.reason),
    listName: capOrNull(text(params.listName), ReadCaps.LIST_NAME),
    count: count === null ? null : Math.trunc(count),
    createdAt: millis(s, 'createdAt'),
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
      map((live) => ActivityFeed.sinceReset(mapEach(live.documents, toEvent))),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
    );
  }
}
