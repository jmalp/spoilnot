// Updates (DESIGN 18.5): the service worker fetches a new version in the background; the app offers it with a
// snackbar (UpdatePrompt) and reloads into it. Not registered in development.
import { Workbox } from 'workbox-window';

type Listener = (apply: () => void) => void;
let onUpdate: Listener | null = null;
let pending: (() => void) | null = null;

/** Called with a function that switches to the new version, when one is waiting. Returns a way to stop. */
export function onUpdateReady(listener: Listener): () => void {
  onUpdate = listener;
  if (pending) listener(pending);
  return () => {
    if (onUpdate === listener) onUpdate = null;
  };
}

export function registerUpdates(): void {
  if (import.meta.env.DEV || !('serviceWorker' in navigator)) return;
  const workbox = new Workbox('/sw.js', { scope: '/' });
  workbox.addEventListener('waiting', () => {
    pending = () => {
      workbox.addEventListener('controlling', () => location.reload());
      workbox.messageSkipWaiting();
    };
    onUpdate?.(pending);
  });
  void workbox.register();
}
