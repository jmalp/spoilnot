import { useEffect, useRef, type PointerEvent, type ReactNode, type RefObject } from 'react';

/** A drag down by this share of the sheet's height dismisses it, like FormSheet's threshold in the app. */
const DISMISS_FRACTION = 0.3;

/** What the sheet needs to know about the visual viewport (the part of the page not under the keyboard). */
export type VisualViewportSize = { height: number; offsetTop: number; scale: number };

/**
 * How much of the bottom of the layout viewport the on-screen keyboard covers (after v1, WEB-14). On an iPhone the
 * keyboard doesn't resize the page (100dvh stays), it shrinks the visual viewport, so a sheet fixed to the bottom
 * would sit under it. 0 while pinch-zoomed, where a smaller visual viewport is not a keyboard.
 */
export function keyboardInset(viewport: VisualViewportSize, layoutHeight: number): number {
  if (Math.abs(viewport.scale - 1) > 0.01) return 0;
  const covered = Math.round(layoutHeight - viewport.height - viewport.offsetTop);
  return covered > 0 ? covered : 0;
}

const isTextField = (el: Element | null): el is HTMLElement =>
  el instanceof HTMLElement && el.matches('input:not([type=checkbox]):not([type=radio]), textarea, select');

/**
 * Keeps [sheet] above the keyboard (Android's imePadding): its bottom follows the visual viewport, it is no taller
 * than what is left, and the focused field scrolls into view inside it, so Save (pinned below the fields) stays
 * visible while typing.
 */
function useFollowKeyboard(ref: RefObject<HTMLDialogElement | null>) {
  useEffect(() => {
    const sheet = ref.current;
    const viewport = window.visualViewport;
    if (!sheet || !viewport) return;
    const reveal = () => {
      const focused = document.activeElement;
      if (isTextField(focused) && sheet.contains(focused)) focused.scrollIntoView({ block: 'nearest' });
    };
    const follow = () => {
      const inset = keyboardInset(viewport, window.innerHeight);
      if (inset > 0) {
        sheet.style.setProperty('--sn-keyboard', `${inset}px`);
        sheet.style.setProperty('--sn-visible-height', `${viewport.height}px`);
        sheet.dataset.keyboard = '';
      } else {
        sheet.style.removeProperty('--sn-keyboard');
        sheet.style.removeProperty('--sn-visible-height');
        delete sheet.dataset.keyboard;
      }
      // After the sheet has moved: the keyboard opening for a field near the bottom.
      requestAnimationFrame(reveal);
    };
    // Moving between fields with the keyboard already open doesn't resize anything.
    const onFocus = () => requestAnimationFrame(reveal);
    follow();
    viewport.addEventListener('resize', follow);
    viewport.addEventListener('scroll', follow);
    sheet.addEventListener('focusin', onFocus);
    return () => {
      viewport.removeEventListener('resize', follow);
      viewport.removeEventListener('scroll', follow);
      sheet.removeEventListener('focusin', onFocus);
    };
  }, [ref]);
}

/**
 * A ModalBottomSheet on a native <dialog>: 28 px top corners, a drag handle (drag down or tap outside to dismiss),
 * at most 640 px wide, scrolling inside, above the on-screen keyboard. [label] names it for VoiceOver.
 */
export function BottomSheet({
  label,
  children,
  onDismiss,
  testId,
}: {
  label: string;
  children: ReactNode;
  onDismiss: () => void;
  testId?: string;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const drag = useRef<{ startY: number; dy: number } | null>(null);
  useEffect(() => {
    const sheet = ref.current;
    // The sheet itself takes focus, as in the app: VoiceOver starts at its name, and the handle shows no focus ring.
    // A field marked data-autofocus takes it instead, so the keyboard opens there (Add item's Name, after v1, UX-09).
    if (sheet) sheet.autofocus = true;
    if (sheet && !sheet.open) sheet.showModal();
    sheet?.querySelector<HTMLElement>('[data-autofocus]')?.focus();
    return () => sheet?.close();
  }, []);
  useFollowKeyboard(ref);

  const move = (event: PointerEvent<HTMLElement>) => {
    if (!drag.current || !ref.current) return;
    drag.current.dy = Math.max(0, event.clientY - drag.current.startY);
    ref.current.style.transform = `translateY(${drag.current.dy}px)`;
  };
  const end = () => {
    const sheet = ref.current;
    if (!drag.current || !sheet) return;
    const dismissed = drag.current.dy > sheet.offsetHeight * DISMISS_FRACTION;
    drag.current = null;
    sheet.style.transition = 'transform 200ms var(--sn-motion-standard)';
    sheet.style.transform = dismissed ? 'translateY(100%)' : '';
    window.setTimeout(() => {
      sheet.style.transition = '';
      if (dismissed) onDismiss();
    }, 200);
  };

  return (
    <dialog
      ref={ref}
      className="m3-sheet"
      aria-label={label}
      data-testid={testId}
      onCancel={(event) => {
        event.preventDefault();
        onDismiss();
      }}
      onClick={(event) => {
        if (event.target === ref.current) onDismiss();
      }}
    >
      <button
        type="button"
        className="m3-sheet__handle"
        aria-label={label}
        onClick={onDismiss}
        onPointerDown={(event) => {
          event.currentTarget.setPointerCapture(event.pointerId);
          drag.current = { startY: event.clientY, dy: 0 };
        }}
        onPointerMove={move}
        onPointerUp={end}
        onPointerCancel={end}
      />
      <div className="m3-sheet__content">{children}</div>
    </dialog>
  );
}
