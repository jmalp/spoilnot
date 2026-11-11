import { str } from '../../core/i18n';
import { Button, Dialog } from '../kit';

/**
 * Sign out found changes that haven't reached the server (after v1, SYNC-09): signing out clears this phone's copy,
 * so they would be lost. From Settings or the household gate (UnsyncedSignOutDialog in the app).
 */
export function UnsyncedSignOutDialog({ onSignOut, onDismiss }: { onSignOut: () => void; onDismiss: () => void }) {
  return (
    <Dialog
      title={str('sign_out_unsynced_title')}
      onDismiss={onDismiss}
      actions={
        <>
          <Button variant="text" onClick={onDismiss}>
            {str('action_cancel')}
          </Button>
          <Button variant="text" danger onClick={onSignOut} data-testid="sign_out_anyway">
            {str('action_sign_out_anyway')}
          </Button>
        </>
      }
    >
      <p>{str('sign_out_unsynced_body')}</p>
    </Dialog>
  );
}
