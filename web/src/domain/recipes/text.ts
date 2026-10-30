// Recipe text rules (DESIGN 11.3, 11.6), ported from RecipeText and SourceUrl: what the web edits and shows. The
// import-only parts (durations, yields, HTML blocks) stay on Android, which adds the recipes.
import { NameKey } from '../pantry/rules';
import { RecipeLimits } from './models';

const SPACES = /[\s\u00A0]+/g;
const ENTITY = /&(#\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);/;

/** The text of [html] as a browser parses it (Jsoup's text() in the app). */
function htmlText(html: string): string {
  if (typeof DOMParser === 'undefined') return html;
  return new DOMParser().parseFromString(html, 'text/html').body.textContent ?? '';
}

export const RecipeText = {
  /** One line of plain text, or null when nothing is left: tags stripped, entities decoded, length capped. */
  clean(text: string | null | undefined, limit: number): string | null {
    if (text == null) return null;
    let plain = text.includes('<') || text.includes('&') ? htmlText(text) : text;
    // Some sites encode twice ("&amp;amp;").
    if (ENTITY.test(plain)) plain = htmlText(plain);
    const cut = plain.replace(SPACES, ' ').trim().slice(0, limit).trim();
    return cut === '' ? null : cut;
  },

  /** Up to [RecipeLimits.MAX_TAGS] distinct tags, in order. */
  tags(values: string[]): string[] {
    const seen = new Set<string>();
    const out: string[] = [];
    for (const value of values.flatMap((v) => v.split(','))) {
      const tag = RecipeText.clean(value, RecipeLimits.TAG);
      if (tag === null || seen.has(NameKey.of(tag))) continue;
      seen.add(NameKey.of(tag));
      out.push(tag);
    }
    return out.slice(0, RecipeLimits.MAX_TAGS);
  },
};

const TRACKING = /^(utm_.*|fbclid|gclid|mc_cid|mc_eid)$/i;

function parseUrl(url: string | null | undefined): URL | null {
  const text = url?.trim();
  if (!text || text.length > RecipeLimits.URL) return null;
  try {
    const parsed = new URL(text);
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return null;
    return parsed.hostname === '' ? null : parsed;
  } catch {
    return null;
  }
}

/** Recipe page addresses: only http and https, without tracking parameters (DESIGN 11.3). */
export const SourceUrl = {
  /** Lowercased scheme and host, no fragment, no default port, no `utm_*`; null for anything but http(s). */
  canonical(url: string | null | undefined): string | null {
    const parsed = parseUrl(url);
    if (!parsed) return null;
    const query = parsed.search
      .slice(1)
      .split('&')
      .filter((p) => p !== '' && !TRACKING.test(p.split('=')[0]!))
      .join('&');
    return `${parsed.protocol}//${parsed.host}${parsed.pathname || '/'}${query ? `?${query}` : ''}`;
  },

  isWebUrl: (url: string | null | undefined): boolean => parseUrl(url) !== null,

  /** "www.seriouseats.com" becomes "seriouseats.com", for the source chip. */
  siteName: (url: string | null | undefined): string | null =>
    parseUrl(url)
      ?.hostname.toLowerCase()
      .replace(/^www\./, '') ?? null,
};
