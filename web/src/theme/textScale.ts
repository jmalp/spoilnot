// The text size setting (DESIGN 18.2). Layout sizes are CSS pixels (dp) and text is in rem, so only text grows,
// like Android's font scale. On an iPhone the root size follows Settings > Accessibility > Larger Text through
// -apple-system-body (17 px at the default size). Tests set a scale in localStorage.
export const TEXT_SCALE_KEY = 'spoilnot.textScale';
const BASE_PX = 16;
const IOS_DEFAULT_BODY_PX = 17;

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

export function applyTextScale(scale = textScale()): void {
  document.documentElement.style.fontSize = `${BASE_PX * scale}px`;
}
