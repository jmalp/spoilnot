// Installing the web app on an iPhone (DESIGN 18.1): Safari's Share > Add to Home Screen. A Home Screen app opens
// full screen and is the only kind of web app iOS gives notifications.

/** An iPhone or iPad (iPadOS reports itself as a Mac with touch). */
export const isIos = (): boolean =>
  /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

/** Opened from the Home Screen (or installed elsewhere), not in a browser tab. */
export const isStandalone = (): boolean =>
  matchMedia('(display-mode: standalone)').matches || (navigator as { standalone?: boolean }).standalone === true;

/** Safari on an iPhone, where Add to Home Screen installs the app. */
export const canAddToHomeScreen = (): boolean => isIos() && !isStandalone();
