import { str } from '../../core/i18n';
import { Button, Dialog } from '../kit';
import { formatHour } from './reminders';

/**
 * The short explanation shown before asking for the notification permission (DESIGN 5.4): when a household opens
 * with dated food on this phone, or a new one reaches its third dated item (after v1, review UX-02). It names the
 * reminder time (UX-03). [onTurnOn] asks the system, which iOS allows only from a tap; [onLater] remembers the answer
 * without asking.
 */
export function NotificationExplainer({
  hour,
  onTurnOn,
  onLater,
}: {
  hour: number;
  onTurnOn: () => void;
  onLater: () => void;
}) {
  return (
    <Dialog
      title={str('notif_explainer_title')}
      onDismiss={onLater}
      actions={
        <>
          <Button variant="text" onClick={onLater}>
            {str('notif_explainer_later')}
          </Button>
          <Button variant="text" data-testid="explainer_turn_on" onClick={onTurnOn}>
            {str('notif_turn_on')}
          </Button>
        </>
      }
    >
      <p>{str('notif_explainer_body_time', formatHour(hour))}</p>
    </Dialog>
  );
}
