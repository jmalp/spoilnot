import { useId } from 'react';
import { str, type StringId } from '../../core/i18n';
import { pushStatus, showTestNotification, type PushStatus } from '../../core/push';
import { useHouseholdSession, useServices } from '../../core/services';
import { EXPIRED_MODES, NotificationRules, type NotificationPrefs } from '../../domain/notifications';
import { Button, ChoiceRow, GroupHeading, ListGroup, Progress, SelectField, SwitchRow, useSnackbars } from '../kit';
import { DetailScaffold } from '../Scaffold';
import { formatHour, useNotificationPrefs, usePushStatus, useTurnOnReminders } from './reminders';
import './notifications.css';

const STAGES: [number, StringId][] = [
  [3, 'stage_3'],
  [2, 'stage_2'],
  [1, 'stage_1'],
  [0, 'stage_0'],
];

/**
 * Settings > Notifications (DESIGN 10.2, 19) in the 2026-11 look: what stops reminders on this phone, the reminder and
 * Meals I'm cooking as switch rows, the hour, the stages as check rows and expired food as radio rows under their
 * headings, and Send test notification as a tonal button.
 */
export function NotificationSettingsScreen({ onBack }: { onBack: () => void }) {
  const me = useHouseholdSession();
  const { notificationPrefs } = useServices();
  const snackbars = useSnackbars();
  const prefs = useNotificationPrefs();
  const status = usePushStatus();
  const update = (change: (p: NotificationPrefs) => NotificationPrefs) => {
    if (prefs) void notificationPrefs.save(me.user.uid, change(prefs)).catch(() => undefined);
  };
  // A Turn on that works says "You're set" here too (DESIGN 5.4).
  const turnOn = useTurnOnReminders();
  const expiredId = useId();
  return (
    <DetailScaffold title={str('settings_notifications')} onBack={onBack} header="pushed" testId="screen_notifications">
      {prefs === null ? (
        <div className="sn-centered">
          <Progress />
        </div>
      ) : (
        <div className="sn-notifications">
          {status !== 'granted' && <PushBanner status={status} onTurnOn={() => void turnOn()} />}
          <ListGroup>
            <SwitchRow
              icon="round/notifications"
              title={str('notif_enabled')}
              summary={str('notif_enabled_help')}
              checked={prefs.enabled}
              onCheckedChange={(enabled) => update((p) => ({ ...p, enabled }))}
              testId="switch_notifications"
            />
            {prefs.enabled && (
              <SwitchRow
                icon="round/soup_kitchen"
                title={str('notif_cooking')}
                summary={str('notif_cooking_help')}
                checked={prefs.cooking}
                onCheckedChange={(cooking) => update((p) => ({ ...p, cooking }))}
                testId="switch_cooking"
              />
            )}
          </ListGroup>
          {prefs.enabled && (
            <>
              <SelectField
                label={str('notif_time')}
                value={String(NotificationRules.hour(prefs))}
                options={NotificationRules.HOURS.map(String)}
                optionLabel={(h) => formatHour(Number(h))}
                onPick={(h) => update((p) => ({ ...p, digestMinutes: Number(h) * 60 }))}
                supportingText={str('notif_time_help', formatHour(NotificationRules.LATEST_HOUR))}
                testId="field_notification_time"
              />
              <section className="sn-notifications__section">
                <GroupHeading text={str('notif_stages')} />
                <ListGroup>
                  {STAGES.map(([stage, label]) => (
                    <ChoiceRow
                      key={stage}
                      label={str(label)}
                      checked={prefs.stages.includes(stage)}
                      onCheckedChange={(on) =>
                        update((p) => ({
                          ...p,
                          stages: NotificationRules.stages(
                            on ? [...p.stages, stage] : p.stages.filter((s) => s !== stage),
                          ),
                        }))
                      }
                    />
                  ))}
                </ListGroup>
              </section>
              <section className="sn-notifications__section">
                <GroupHeading text={str('notif_expired')} id={expiredId} />
                <ListGroup role="radiogroup" aria-labelledby={expiredId}>
                  {EXPIRED_MODES.map((mode) => (
                    <ChoiceRow
                      key={mode}
                      label={str(`expired_${mode}` as StringId)}
                      checked={prefs.expiredMode === mode}
                      radioName={expiredId}
                      onCheckedChange={() => update((p) => ({ ...p, expiredMode: mode }))}
                    />
                  ))}
                </ListGroup>
              </section>
            </>
          )}
          <Button
            variant="tonal"
            full
            large
            icon="round/notifications_active"
            className="t-button-medium"
            data-testid="button_test_notification"
            onClick={() =>
              void (async () => {
                if (await showTestNotification()) snackbars.show(str('notif_test_sent'));
                else if (pushStatus() === 'default') await turnOn();
              })()
            }
          >
            {str('notif_test')}
          </Button>
        </div>
      )}
    </DetailScaffold>
  );
}

/**
 * What stops reminders on this phone (DESIGN 5.4), as a rounded errorContainer banner (19.11): not allowed yet (Turn
 * on), turned off in the iPhone's Settings, or Safari outside the Home Screen. The app's button to the system
 * settings has no web counterpart.
 */
function PushBanner({ status, onTurnOn }: { status: PushStatus; onTurnOn: () => void }) {
  const help: StringId | null =
    status === 'denied' ? 'web_notif_denied' : status === 'install' ? 'web_notif_install' : null;
  return (
    <div className="sn-notifications__banner" data-testid="notifications_banner">
      <p className="t-body-large">{str(status === 'unsupported' ? 'web_notif_unsupported' : 'notif_blocked')}</p>
      {help && <p className="t-body-large">{str(help)}</p>}
      {status === 'default' && (
        <Button
          variant="text"
          className="t-button-medium"
          onClick={onTurnOn}
          data-testid="button_turn_on_notifications"
        >
          {str('notif_turn_on')}
        </Button>
      )}
    </div>
  );
}
