import { useEffect, useRef, type PointerEvent, type ReactNode } from 'react';

/** A drag down by this share of the sheet's height dismisses it, like FormSheet's threshold in the app. */
const DISMISS_FRACTION = 0.3;

/**
 * A ModalBottomSheet on a native <dialog>: 28 px top corners, a drag handle (drag down or tap outside to dismiss),
 * at most 640 px wide, scrolling inside. [label] names it for VoiceOver.
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
    if (sheet && !sheet.open) sheet.showModal();
    return () => sheet?.close();
  }, []);

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
