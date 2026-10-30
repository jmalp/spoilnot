// One listener on the recipe box shared by every recipe screen (RecipeStore in the app).
import { distinctUntilChanged, map, of, ReplaySubject, share, switchMap, timer, type Observable } from 'rxjs';
import type { SessionManager } from '../session';
import type { RecipeBoxData, RecipeRepository } from './models';

/** How long the listener stays after the last recipe screen goes away. */
const LINGER_MS = 5_000;

/**
 * The household's recipes, or null outside a household. It starts with the first recipe screen (DESIGN 9.1) and
 * stops a few seconds after the last one goes away, so moving between them reads nothing again.
 */
export class RecipeStore {
  readonly box$: Observable<RecipeBoxData | null>;

  constructor(session: SessionManager, repository: RecipeRepository) {
    this.box$ = session.session$.pipe(
      map((s) => (s.kind === 'inHousehold' ? s.householdId : null)),
      distinctUntilChanged(),
      switchMap((hid) => (hid === null ? of(null) : repository.observe(hid))),
      share({ connector: () => new ReplaySubject(1), resetOnRefCountZero: () => timer(LINGER_MS) }),
    );
  }
}
