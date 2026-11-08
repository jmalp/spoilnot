import { useEffect, useState } from 'react';
import { messageId } from '../../core/errors';
import { useAction } from '../../core/hooks';
import { str } from '../../core/i18n';
import { useServices } from '../../core/services';
import { PasswordField } from '../forms';
import { Button, Dialog, ErrorText } from '../kit';

/**
 * Delete account (DESIGN 5.1), from Settings or the household gate: the password dialog and its error. Once the
 * deletion starts, Loading replaces the screen; if it then fails and leaves the user at the gate, the gate opens the
 * dialog again with the error (SessionManager.deleteFailure), once.
 */
export function useDeleteAccount(): { open: () => void; dialog: React.ReactNode } {
  const { session } = useServices();
  const action = useAction();
  const [earlier, setEarlier] = useState(() => session.deleteFailure);
  const [open, setOpen] = useState(earlier !== null);
  useEffect(() => session.dismissDeleteFailure(), [session]);
  const error = action.error ?? (earlier !== null ? messageId(earlier) : null);
  return {
    open: () => {
      action.clearError();
      setEarlier(null);
      setOpen(true);
    },
    dialog: open && (
      <DeleteAccountDialog
        busy={action.busy}
        error={error && str(error)}
        onDismiss={() => {
          setEarlier(null);
          setOpen(false);
        }}
        onDelete={(password) => {
          setEarlier(null);
          void action.run(() => session.deleteAccount(password));
        }}
      />
    ),
  };
}

function DeleteAccountDialog({
  busy,
  error,
  onDismiss,
  onDelete,
}: {
  busy: boolean;
  error: string | null;
  onDismiss: () => void;
  onDelete: (password: string) => void;
}) {
  const [password, setPassword] = useState('');
  return (
    <Dialog
      title={str('delete_account_title')}
      onDismiss={onDismiss}
      actions={
        <>
          <Button variant="text" onClick={onDismiss}>
            {str('action_cancel')}
          </Button>
          <Button variant="text" danger disabled={busy || password === ''} onClick={() => onDelete(password)}>
            {str('action_delete')}
          </Button>
        </>
      }
    >
      <div className="sn-dialog-form">
        <p>{str('delete_account_body')}</p>
        <PasswordField value={password} onValueChange={setPassword} />
        <ErrorText text={error} />
      </div>
    </Dialog>
  );
}
