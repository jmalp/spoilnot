import { useEffect, useRef, type ReactNode } from 'react';

/**
 * An AlertDialog on a native <dialog>: focus stays inside, Escape and a tap outside dismiss it, VoiceOver reads it
 * as a dialog. [actions] are text buttons, confirm last.
 */
export function Dialog({
  title,
  children,
  actions,
  onDismiss,
  titleClassName = 't-headline-small',
}: {
  title?: string;
  children?: ReactNode;
  actions: ReactNode;
  onDismiss: () => void;
  /** The title's text style; the 2026-11 look's choice dialogs use t-dialog-title (DESIGN 19.11). */
  titleClassName?: string;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    if (dialog && !dialog.open) dialog.showModal();
    return () => dialog?.close();
  }, []);
  return (
    <dialog
      ref={ref}
      className="m3-dialog"
      aria-label={title}
      onCancel={(event) => {
        event.preventDefault();
        onDismiss();
      }}
      onClick={(event) => {
        if (event.target === ref.current) onDismiss();
      }}
    >
      {title && <h2 className={`m3-dialog__title ${titleClassName}`}>{title}</h2>}
      {children && <div className="m3-dialog__body t-body-medium">{children}</div>}
      <div className="m3-dialog__actions">{actions}</div>
    </dialog>
  );
}
