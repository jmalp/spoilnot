import { str } from '../../core/i18n';
import { requestPushPermission } from '../../core/push';
import { useServices } from '../../core/services';
import { Button, Dialog } from '../kit';

/**
 * The short explanation shown before asking for the notification permission, once the third dated item is saved
 * (DESIGN 5.4). Asks the system only if the user chooses Turn on; iOS asks only from a tap.
 */
export function NotificationExplainer({ onDone }: { onDone: () => void }) {
  const { deviceSync } = useServices();
  return (
    <Dialog
      title={str('notif_explainer_title')}
      onDismiss={onDone}
      actions={
        <>
          <Button variant="text" onClick={onDone}>
            {str('notif_explainer_later')}
          </Button>
          <Button
            variant="text"
            data-testid="explainer_turn_on"
            onClick={() =>
              void requestPushPermission().then(async (status) => {
                if (status === 'granted') await deviceSync.permissionGranted();
                onDone();
              })
            }
          >
            {str('notif_turn_on')}
          </Button>
        </>
      }
    >
      <p>{str('notif_explainer_body')}</p>
    </Dialog>
  );
}
