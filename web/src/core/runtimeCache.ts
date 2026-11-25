// The files the service worker leaves out of its precache (DESIGN 18.5, after v1 review EFF-06) and caches when they
// are first used instead: the licenses list, read only by Settings > About, and the font subsets an English screen
// never draws (Cyrillic, Greek, Vietnamese, Hebrew), which the browser fetches only for a name that needs them.
// vite.config.ts's globIgnores and this file name the same files.

/** Cache names, apart from Workbox's precache. */
export const RUNTIME_CACHES = { licenses: 'spoilnot-licenses', fonts: 'spoilnot-fonts' } as const;

/** Precache globs to leave out (vite.config.ts): the licenses and the font subsets other than Latin. */
export const PRECACHE_IGNORES = [
  '**/licenses.json',
  '**/*-{cyrillic,cyrillic-ext,greek,vietnamese,hebrew}-*.woff2',
] as const;

const OTHER_SCRIPTS = /-(cyrillic|cyrillic-ext|greek|vietnamese|hebrew)-[^/]*\.woff2$/;

export type RuntimeRoute =
  /** Network first, then the copy from the last time it was read (the list changes with each release). */
  | { cache: typeof RUNTIME_CACHES.licenses; strategy: 'network-first' }
  /** Cache first: hashed files never change. */
  | { cache: typeof RUNTIME_CACHES.fonts; strategy: 'cache-first' };

/** Which runtime cache, if any, serves a GET for this URL. Only the site's own files. */
export function runtimeRouteFor(url: string, origin: string): RuntimeRoute | null {
  const parsed = new URL(url);
  if (parsed.origin !== origin) return null;
  if (parsed.pathname === '/licenses.json') return { cache: RUNTIME_CACHES.licenses, strategy: 'network-first' };
  if (parsed.pathname.startsWith('/assets/') && OTHER_SCRIPTS.test(parsed.pathname)) {
    return { cache: RUNTIME_CACHES.fonts, strategy: 'cache-first' };
  }
  return null;
}

/** Answers a request by its route, keeping successful responses in that route's cache. */
export async function respond(request: Request, route: RuntimeRoute, caches: CacheStorage): Promise<Response> {
  const cache = await caches.open(route.cache);
  if (route.strategy === 'cache-first') {
    const cached = await cache.match(request);
    if (cached) return cached;
    const response = await fetch(request);
    if (response.ok) await cache.put(request, response.clone());
    return response;
  }
  try {
    const response = await fetch(request);
    if (response.ok) await cache.put(request, response.clone());
    return response;
  } catch (error) {
    const cached = await cache.match(request);
    if (cached) return cached;
    throw error;
  }
}
