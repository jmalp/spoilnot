import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { str } from '../../core/i18n';
import { Button } from './Button';

/** How long Undo stays available, as UNDO_WINDOW_MS in the app (DESIGN 5.2). */
export const UNDO_WINDOW_MS = 8_000;
/** SnackbarDuration.Short. */
export const SHORT_MS = 4_000;
/** SnackbarDuration.Long. */
export const LONG_MS = 10_000;
/**
 * SnackbarDuration.Indefinite: until its action is tapped. Another message shows first and this one comes back after
 * it, so an Undo is never hidden behind it.
 */
export const INDEFINITE = Number.POSITIVE_INFINITY;

type Message = { id: number; text: string; action?: { label: string; run: () => void }; durationMs: number };

type Snackbars = {
  /** A message for 4 seconds. */
  show: (text: string) => void;
  /**
   * A message with Undo for 8 seconds (MessageEffect in the app), not counting the time it has focus or the pointer
   * over it.
   */
  showUndo: (text: string, undo: () => void) => void;
  /** A message with another action, for [durationMs] (SnackbarDuration.Long by default, or INDEFINITE). */
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
    const added = { ...message, id: next.current++ };
    // Messages without a time limit wait at the back, so one that is showing gives way and comes back after.
    setQueue((current) => {
      const at = added.durationMs === INDEFINITE ? -1 : current.findIndex((m) => m.durationMs === INDEFINITE);
      return at < 0 ? [...current, added] : [...current.slice(0, at), added, ...current.slice(at)];
    });
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
  // The timer waits while the snackbar has focus or the pointer is over it, and goes on with the time left after
  // (after v1, review A11Y-02: Android takes its time from the accessibility settings, which a web page can't read).
  const [hold, setHold] = useState<{ id: number; focus: boolean; pointer: boolean } | null>(null);
  const held = current !== undefined && hold?.id === current.id && (hold.focus || hold.pointer);
  const holdBy = (part: 'focus' | 'pointer', on: boolean) =>
    current &&
    setHold((h) => ({ ...(h?.id === current.id ? h : { focus: false, pointer: false }), id: current.id, [part]: on }));
  const left = useRef<{ id: number; ms: number } | null>(null);
  useEffect(() => {
    if (!current || current.durationMs === INDEFINITE) return;
    if (left.current?.id !== current.id) left.current = { id: current.id, ms: current.durationMs };
    if (held) return;
    const time = left.current;
    const started = Date.now();
    const timer = window.setTimeout(() => dismiss(current.id), time.ms);
    return () => {
      window.clearTimeout(timer);
      time.ms -= Date.now() - started;
    };
  }, [current, dismiss, held]);
  return (
    <Context.Provider value={value}>
      {children}
      <div className="m3-snackbar-host" aria-live="polite" style={{ paddingBottom: 12 + bottomInset }}>
        {current && (
          <div
            key={current.id}
            className="m3-snackbar t-body-medium"
            role="status"
            data-held={held || undefined}
            onFocus={() => holdBy('focus', true)}
            onBlur={(e) => {
              if (!e.currentTarget.contains(e.relatedTarget as Node | null)) holdBy('focus', false);
            }}
            onPointerEnter={() => holdBy('pointer', true)}
            onPointerLeave={() => holdBy('pointer', false)}
          >
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
