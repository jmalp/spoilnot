import type { Observable } from 'rxjs';
import type { PantryData } from './models';

/** Products and batches. Writes go through the HouseholdWriter. */
export interface PantryRepository {
  observe(householdId: string): Observable<PantryData>;
}
