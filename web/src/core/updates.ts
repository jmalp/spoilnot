// Updates (DESIGN 18.5): the service worker fetches a new version in the background; the app offers it with a
// snackbar (UpdatePrompt) and reloads into it. The browser looks for one only when the app is loaded, and a Home
// Screen app can stay open for days, so the app also asks when it becomes visible, at most once an hour
// (UpdateChecks), and right away when config/app requires a newer build. Not registered in development.
import { Workbox } from 'workbox-window';
import { systemClock } from '../domain/time';
import { UpdateChecks } from '../domain/updates';

type Listener = (apply: () => void) => void;
let onUpdate: Listener | null = null;
let pending: (() => void) | null = null;
let workbox: Workbox | null = null;
let required = false;
const checks = new UpdateChecks(systemClock);

/** Called with a function that switches to the new version, when one is waiting. Returns a way to stop. */
export function onUpdateReady(listener: Listener): () => void {
  onUpdate = listener;
  if (pending) listener(pending);
  return () => {
    if (onUpdate === listener) onUpdate = null;
  };
}

/** config/app requires a newer build (or no longer does): look for it now, and at every visit until it waits. */
export function setUpdateRequired(value: boolean): void {
  required = value;
  lookIfDue();
}

function lookIfDue() {
  if (!workbox || !checks.shouldCheck({ required, waiting: pending !== null })) return;
  // Offline, or the server can't be reached: the next visit looks again.
  workbox.update().catch(() => undefined);
}

export function registerUpdates(): void {
  if (import.meta.env.DEV || !('serviceWorker' in navigator)) return;
  const wb = new Workbox('/sw.js', { scope: '/' });
  wb.addEventListener('waiting', () => {
    pending = () => {
      wb.addEventListener('controlling', () => location.reload());
      wb.messageSkipWaiting();
    };
    onUpdate?.(pending);
  });
  // Registering is a look of its own.
  void wb.register().then(() => {
    workbox = wb;
    checks.checked();
    if (required) lookIfDue();
  });
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') lookIfDue();
  });
}
