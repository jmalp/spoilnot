import type { Observable } from 'rxjs';
import type { PantryData } from './models';

/** Products and batches. Writes go through the HouseholdWriter. */
export interface PantryRepository {
  /**
   * The household's products and batches, from one pair of changes-only listeners per household shared by every
   * screen; they stop a few seconds after the last screen leaves (after v1, DESIGN 9.1).
   */
  observe(householdId: string): Observable<PantryData>;
  /**
   * The same from the local cache only, with no server listener (the restock engine, DESIGN 9.6). `serverRead` is
   * true once a full read of products and of stock is on record on this phone.
   */
  observeCached(householdId: string): Observable<PantryData>;
}
