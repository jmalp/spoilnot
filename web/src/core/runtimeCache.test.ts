import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it, vi } from 'vitest';
import { RUNTIME_CACHES, respond, runtimeRouteFor } from './runtimeCache';

const ORIGIN = 'https://spoilnot-prod.web.app';

/** The hashed name Vite gives a font file, as in dist/assets. */
const built = (file: string) => `${ORIGIN}/assets/${file.replace('.woff2', '-DM6njrJ3.woff2')}`;

/** A CacheStorage with one cache in memory. */
function memoryCaches() {
  const entries = new Map<string, Response>();
  const cache = {
    match: vi.fn(async (request: Request) => entries.get(request.url)?.clone()),
    put: vi.fn(async (request: Request, response: Response) => void entries.set(request.url, response)),
  };
  const caches = { open: vi.fn(async () => cache) } as unknown as CacheStorage;
  return { caches, cache, entries };
}

describe('runtime cache', () => {
  it('serves the licenses network first and the other scripts’ font subsets cache first', () => {
    expect(runtimeRouteFor(`${ORIGIN}/licenses.json`, ORIGIN)).toEqual({
      cache: RUNTIME_CACHES.licenses,
      strategy: 'network-first',
    });
    expect(runtimeRouteFor(built('manrope-cyrillic-ext-wght-normal.woff2'), ORIGIN)).toEqual({
      cache: RUNTIME_CACHES.fonts,
      strategy: 'cache-first',
    });
    // Precached, so the service worker's precache answers them.
    expect(runtimeRouteFor(built('manrope-latin-wght-normal.woff2'), ORIGIN)).toBeNull();
    expect(runtimeRouteFor(built('fredoka-latin-ext-wght-normal.woff2'), ORIGIN)).toBeNull();
    expect(runtimeRouteFor(`${ORIGIN}/assets/index-DJPSc1mi.js`, ORIGIN)).toBeNull();
    // Only the site's own files.
    expect(runtimeRouteFor('https://example.com/licenses.json', ORIGIN)).toBeNull();
  });

  it('routes every font subset but Latin and Latin Extended of the bundled fonts', () => {
    for (const font of ['manrope', 'fredoka']) {
      // Tests run in web/.
      const dir = join('node_modules', '@fontsource-variable', font);
      const subsets = Object.keys(JSON.parse(readFileSync(join(dir, 'unicode.json'), 'utf8')) as object);
      const files = readdirSync(join(dir, 'files')).filter((file) => file.endsWith('-wght-normal.woff2'));
      expect(files).toHaveLength(subsets.length);
      for (const file of files) {
        const latin = /-latin(-ext)?-wght/.test(file);
        expect(runtimeRouteFor(built(file), ORIGIN), file).toEqual(
          latin ? null : { cache: RUNTIME_CACHES.fonts, strategy: 'cache-first' },
        );
      }
    }
  });

  it('keeps the licenses it read and shows them offline', async () => {
    const { caches, entries } = memoryCaches();
    const request = new Request(`${ORIGIN}/licenses.json`);
    const route = runtimeRouteFor(request.url, ORIGIN)!;
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response('{"v":1}'));
    expect(await (await respond(request, route, caches)).text()).toBe('{"v":1}');
    expect(entries.has(request.url)).toBe(true);

    fetchMock.mockRejectedValueOnce(new TypeError('offline'));
    expect(await (await respond(request, route, caches)).text()).toBe('{"v":1}');

    entries.clear();
    fetchMock.mockRejectedValueOnce(new TypeError('offline'));
    await expect(respond(request, route, caches)).rejects.toThrow('offline');
    fetchMock.mockRestore();
  });

  it('fetches a font subset once and keeps no failed answer', async () => {
    const { caches, cache } = memoryCaches();
    const request = new Request(built('manrope-greek-wght-normal.woff2'));
    const route = runtimeRouteFor(request.url, ORIGIN)!;
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response('missing', { status: 404 }))
      .mockResolvedValueOnce(new Response('font'));
    expect((await respond(request, route, caches)).status).toBe(404);
    expect(cache.put).not.toHaveBeenCalled();
    expect(await (await respond(request, route, caches)).text()).toBe('font');
    expect(await (await respond(request, route, caches)).text()).toBe('font');
    expect(fetchMock).toHaveBeenCalledTimes(2);
    fetchMock.mockRestore();
  });
});
