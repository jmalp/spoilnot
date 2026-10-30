// The text size setting (DESIGN 18.2). Layout sizes are CSS pixels (dp) and only text grows, like Android's font
// scale. On an iPhone the scale follows Settings > Accessibility > Larger Text through -apple-system-body (17 px at
// the default size). Tests set a scale in localStorage.
//
// Text grows non-linearly, as on Android 14 and later (FontScaleConverterFactory): large text grows less than small
// text, so headlines stay on screen at 200 %. Each size the app uses is a CSS variable, --sp-<n>, the n sp size at
// the current scale; the root font size still follows the scale linearly for letter spacing.
export const TEXT_SCALE_KEY = 'spoilnot.textScale';
const BASE_PX = 16;
const IOS_DEFAULT_BODY_PX = 17;

/** Android's lookup tables (sp to dp) by font scale, from FontScaleConverterFactory. */
const FROM_SP = [8, 10, 12, 14, 18, 20, 24, 30, 100];
const TABLES: [number, number[]][] = [
  [1.15, [9.2, 11.5, 13.8, 16.4, 19.8, 21.8, 25.2, 30, 100]],
  [1.3, [10.4, 13, 15.6, 18.8, 21.6, 23.6, 26.4, 30, 100]],
  [1.5, [12, 15, 18, 22, 24, 26, 28, 30, 100]],
  [1.8, [14.4, 18, 21.6, 24.4, 27.6, 30.8, 32.8, 34.8, 100]],
  [2, [16, 20, 24, 26, 30, 34, 36, 38, 100]],
];
/** [sp] through one table, interpolating between its points (FontScaleConverterTable.lookupAndInterpolate). */
function lookup(toDp: number[], sp: number): number {
  const from = [0, ...FROM_SP];
  const to = [0, ...toDp];
  const end = from.findIndex((x) => x >= sp);
  if (end < 0) return (sp * to[to.length - 1]!) / from[from.length - 1]!; // past the table: its last ratio
  if (end === 0) return 0;
  const [x0, x1, y0, y1] = [from[end - 1]!, from[end]!, to[end - 1]!, to[end]!];
  return y0 + ((sp - x0) * (y1 - y0)) / (x1 - x0);
}

/**
 * The size in CSS pixels of [sp] at [scale] (FontScaleConverterFactory.forScale): linear below the first table,
 * a table at its scale, and between two tables a mix of both. iPhone accessibility sizes go past Android's 200 %:
 * there the 200 % table grows with the scale.
 */
export function scaledSp(sp: number, scale: number): number {
  const last = TABLES[TABLES.length - 1]!;
  if (scale < TABLES[0]![0]) return sp * scale;
  if (scale >= last[0]) return lookup(last[1], sp) * (scale / last[0]);
  const upper = TABLES.findIndex(([key]) => key >= scale);
  const [highScale, high] = TABLES[upper]!;
  if (highScale === scale) return lookup(high, sp);
  const [lowScale, low] = TABLES[upper - 1]!;
  const t = (scale - lowScale) / (highScale - lowScale);
  return lookup(low, sp) + t * (lookup(high, sp) - lookup(low, sp));
}

export function textScale(): number {
  try {
    const forced = Number(localStorage.getItem(TEXT_SCALE_KEY));
    if (forced > 0) return forced;
  } catch {
    // Storage can be unavailable (private browsing); the system size still applies.
  }
  const probe = document.createElement('div');
  probe.style.font = '-apple-system-body';
  if (!probe.style.font) return 1; // not Safari: browsers apply their own text zoom
  document.body.append(probe);
  const px = parseFloat(getComputedStyle(probe).fontSize);
  probe.remove();
  return px > 0 ? px / IOS_DEFAULT_BODY_PX : 1;
}

/** The text sizes in the type scale and the kit, in sp. */
export const SP_SIZES = [11, 12, 14, 16, 20, 22, 24, 28, 32, 36, 40, 44, 45, 52, 57, 64] as const;

let applied = 1;

/** The scale applyTextScale set, for layouts that change at large sizes (fontScale in the app). */
export const appliedTextScale = (): number => applied;

export function applyTextScale(scale = textScale()): void {
  applied = scale;
  const root = document.documentElement.style;
  root.fontSize = `${BASE_PX * scale}px`;
  for (const sp of SP_SIZES) root.setProperty(`--sp-${sp}`, `${Math.round(scaledSp(sp, scale) * 100) / 100}px`);
}
