import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { str } from '../../core/i18n';
import { Button } from './Button';

/** How long Undo stays available, as UNDO_WINDOW_MS in the app (DESIGN 5.2). */
export const UNDO_WINDOW_MS = 8_000;
/** SnackbarDuration.Short. */
export const SHORT_MS = 4_000;
/** SnackbarDuration.Long. */
export const LONG_MS = 10_000;

type Message = { id: number; text: string; action?: { label: string; run: () => void }; durationMs: number };

type Snackbars = {
  /** A message for 4 seconds. */
  show: (text: string) => void;
  /** A message with Undo for 8 seconds (MessageEffect in the app). */
  showUndo: (text: string, undo: () => void) => void;
  /** A message with another action, for [durationMs] (SnackbarDuration.Long by default). */
  showAction: (text: string, label: string, run: () => void, durationMs?: number) => void;
  /** Room to leave below snackbars, such as a floating action button's (Scaffold puts snackbars above it). */
  setBottomInset: (px: number) => void;
};

const Context = createContext<Snackbars | null>(null);

export function useSnackbars(): Snackbars {
  const value = useContext(Context);
  if (!value) throw new Error('useSnackbars outside SnackbarProvider');
  return value;
}

/** The snackbars, or null on screens shown before sign-in, which have none. */
export function useOptionalSnackbars(): Snackbars | null {
  return useContext(Context);
}

/** Shows messages one at a time, in order, at the bottom of the screen and above the navigation bar. */
export function SnackbarProvider({ children }: { children: ReactNode }) {
  const [queue, setQueue] = useState<Message[]>([]);
  const [bottomInset, setBottomInset] = useState(0);
  const next = useRef(0);
  const enqueue = useCallback((message: Omit<Message, 'id'>) => {
    setQueue((current) => [...current, { ...message, id: next.current++ }]);
  }, []);
  const value = useMemo<Snackbars>(
    () => ({
      show: (text) => enqueue({ text, durationMs: SHORT_MS }),
      showUndo: (text, undo) =>
        enqueue({ text, durationMs: UNDO_WINDOW_MS, action: { label: str('action_undo'), run: undo } }),
      showAction: (text, label, run, durationMs = LONG_MS) => enqueue({ text, durationMs, action: { label, run } }),
      setBottomInset,
    }),
    [enqueue],
  );
  const current = queue[0];
  const dismiss = useCallback((id: number) => setQueue((q) => q.filter((m) => m.id !== id)), []);
  useEffect(() => {
    if (!current) return;
    const timer = window.setTimeout(() => dismiss(current.id), current.durationMs);
    return () => window.clearTimeout(timer);
  }, [current, dismiss]);
  return (
    <Context.Provider value={value}>
      {children}
      <div className="m3-snackbar-host" aria-live="polite" style={{ paddingBottom: 12 + bottomInset }}>
        {current && (
          <div key={current.id} className="m3-snackbar t-body-medium" role="status">
            <span className="m3-snackbar__text">{current.text}</span>
            {current.action && (
              <Button
                variant="text"
                onClick={() => {
                  current.action?.run();
                  dismiss(current.id);
                }}
              >
                {current.action.label}
              </Button>
            )}
          </div>
        )}
      </div>
    </Context.Provider>
  );
}
