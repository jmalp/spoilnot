// Whether the page is on screen: the web's stand-in for Android's lifecycle (STARTED), so screens let go of their
// listeners when the app leaves the screen (after v1, DESIGN 9.1 and 18.3).
import { distinctUntilChanged, filter, fromEvent, map, of, shareReplay, startWith, type Observable } from 'rxjs';

const isVisible = (doc: Document): boolean => doc.visibilityState !== 'hidden';

/** True while the page is on screen, now and on every change. */
export function visibility$(doc: Document | undefined = globalThis.document): Observable<boolean> {
  if (!doc) return of(true);
  return fromEvent(doc, 'visibilitychange').pipe(
    startWith(null),
    map(() => isVisible(doc)),
    distinctUntilChanged(),
  );
}

/** The page's visibility, shared by every screen and listener. */
export const pageVisible$: Observable<boolean> = visibility$().pipe(shareReplay({ bufferSize: 1, refCount: true }));

/** Emits each time the page leaves the screen (a Home Screen app sent to the background, a locked phone). */
export const pageHidden$: Observable<unknown> = pageVisible$.pipe(filter((visible) => !visible));
