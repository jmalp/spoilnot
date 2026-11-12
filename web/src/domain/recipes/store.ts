// One listener on the recipe box shared by every recipe screen (RecipeStore in the app).
import {
  distinctUntilChanged,
  map,
  NEVER,
  of,
  race,
  ReplaySubject,
  share,
  switchMap,
  take,
  timer,
  type Observable,
} from 'rxjs';
import { LoadRetry, retryLoading } from '../load';
import type { SessionManager } from '../session';
import type { RecipeBoxData, RecipeRepository } from './models';

/** How long the listener stays after the last recipe screen goes away. */
const LINGER_MS = 5_000;

/**
 * The household's recipes, or null outside a household. It starts with the first recipe screen (DESIGN 9.1) and
 * stops a few seconds after the last one goes away, so moving between them reads nothing again. While the page is off
 * screen ([stopAtOnce$], after v1) it stops without waiting, as the pantry's shared listeners do (domain/shared).
 */
export class RecipeStore {
  readonly box$: Observable<RecipeBoxData | null>;
  /** "Couldn't load. Retry" on the Recipes tab (DESIGN 6.7): the box listens again instead of ending (WEB-10). */
  readonly load = new LoadRetry();

  constructor(session: SessionManager, repository: RecipeRepository, stopAtOnce$: Observable<unknown> = NEVER) {
    this.box$ = session.session$.pipe(
      map((s) => (s.kind === 'inHousehold' ? s.householdId : null)),
      distinctUntilChanged(),
      switchMap((hid) => (hid === null ? of(null) : repository.observe(hid).pipe(retryLoading(this.load)))),
      share({
        connector: () => new ReplaySubject(1),
        resetOnRefCountZero: () => race(timer(LINGER_MS), stopAtOnce$.pipe(take(1))),
      }),
    );
  }
}
