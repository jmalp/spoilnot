import { useCallback, useEffect, useId, useMemo, useState } from 'react';
import { useObservable } from '../../core/hooks';
import { str, type StringId } from '../../core/i18n';
import { pushStatus, requestPushPermission, showTestNotification, type PushStatus } from '../../core/push';
import { useHouseholdSession, useServices } from '../../core/services';
import { EXPIRED_MODES, NotificationRules, type NotificationPrefs } from '../../domain/notifications';
import { Button, Card, Checkbox, Progress, Radio, SelectField, Switch, useSnackbars } from '../kit';
import { DetailScaffold } from '../Scaffold';
import './notifications.css';

/** "9:00 AM" in the phone's language (FormatStyle.SHORT in the app). */
export const formatHour = (hour: number): string =>
  new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(new Date(2000, 0, 1, hour));

/** The system's notification permission, checked again when the user comes back from the iPhone's Settings. */
export function usePushStatus(): [PushStatus, () => void] {
  const [status, setStatus] = useState(pushStatus);
  const refresh = useCallback(() => setStatus(pushStatus()), []);
  useEffect(() => {
    document.addEventListener('visibilitychange', refresh);
    return () => document.removeEventListener('visibilitychange', refresh);
  }, [refresh]);
  return [status, refresh];
}

/** This phone's notification settings, as a store shared with Settings' row (NotificationSettingsViewModel). */
export function useNotificationPrefs(): NotificationPrefs | null {
  const me = useHouseholdSession();
  const { notificationPrefs } = useServices();
  return useObservable<NotificationPrefs | null>(
    useMemo(() => notificationPrefs.observe(me.user.uid), [notificationPrefs, me.user.uid]),
    null,
  );
}

const STAGES: [number, StringId][] = [
  [3, 'stage_3'],
  [2, 'stage_2'],
  [1, 'stage_1'],
  [0, 'stage_0'],
];

/** Settings > Notifications (DESIGN 10.2): on or off, the hour, meals, stages and expired food. */
export function NotificationSettingsScreen({ onBack }: { onBack: () => void }) {
  const me = useHouseholdSession();
  const { notificationPrefs, deviceSync } = useServices();
  const snackbars = useSnackbars();
  const prefs = useNotificationPrefs();
  const [status, refresh] = usePushStatus();
  const update = (change: (p: NotificationPrefs) => NotificationPrefs) => {
    if (prefs) void notificationPrefs.save(me.user.uid, change(prefs)).catch(() => undefined);
  };
  const turnOn = async () => {
    const answer = await requestPushPermission();
    refresh();
    if (answer === 'granted') await deviceSync.permissionGranted();
  };
  return (
    <DetailScaffold title={str('settings_notifications')} onBack={onBack} testId="screen_notifications">
      {prefs === null ? (
        <div className="sn-centered">
          <Progress />
        </div>
      ) : (
        <div className="sn-notifications">
          {status !== 'granted' && <PushBanner status={status} onTurnOn={() => void turnOn()} />}
          <ToggleRow
            title={str('notif_enabled')}
            help={str('notif_enabled_help')}
            checked={prefs.enabled}
            onChange={(enabled) => update((p) => ({ ...p, enabled }))}
            testId="switch_notifications"
          />
          {prefs.enabled && (
            <>
              <SelectField
                label={str('notif_time')}
                value={String(NotificationRules.hour(prefs))}
                options={NotificationRules.HOURS.map(String)}
                optionLabel={(h) => formatHour(Number(h))}
                onPick={(h) => update((p) => ({ ...p, digestMinutes: Number(h) * 60 }))}
                supportingText={str('notif_time_help')}
                testId="field_notification_time"
              />
              <ToggleRow
                title={str('notif_cooking')}
                help={str('notif_cooking_help')}
                checked={prefs.cooking}
                onChange={(cooking) => update((p) => ({ ...p, cooking }))}
                testId="switch_cooking"
              />
              <h2 className="t-title-medium">{str('notif_stages')}</h2>
              <div className="sn-notifications__choices">
                {STAGES.map(([stage, label]) => (
                  <label key={stage} className="sn-notifications__choice t-body-large">
                    <Checkbox
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
                    {str(label)}
                  </label>
                ))}
              </div>
              <ExpiredModes prefs={prefs} onPick={(expiredMode) => update((p) => ({ ...p, expiredMode }))} />
            </>
          )}
          <Button
            variant="outlined"
            full
            large
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
 * What stops reminders on this phone (DESIGN 5.4): not allowed yet (Turn on), turned off in the iPhone's Settings,
 * or Safari outside the Home Screen. The app's button to the system settings has no web counterpart.
 */
function PushBanner({ status, onTurnOn }: { status: PushStatus; onTurnOn: () => void }) {
  const help: StringId | null =
    status === 'denied' ? 'web_notif_denied' : status === 'install' ? 'web_notif_install' : null;
  return (
    <Card variant="filled" className="sn-notifications__banner" data-testid="notifications_banner">
      <p className="t-body-large">{str(status === 'unsupported' ? 'web_notif_unsupported' : 'notif_blocked')}</p>
      {help && <p className="t-body-large">{str(help)}</p>}
      {status === 'default' && (
        <Button variant="text" onClick={onTurnOn} data-testid="button_turn_on_notifications">
          {str('notif_turn_on')}
        </Button>
      )}
    </Card>
  );
}

function ToggleRow({
  title,
  help,
  checked,
  onChange,
  testId,
}: {
  title: string;
  help: string;
  checked: boolean;
  onChange: (on: boolean) => void;
  testId: string;
}) {
  return (
    <label className="sn-notifications__toggle" data-testid={testId}>
      <span className="sn-notifications__toggle-text">
        <span className="t-title-medium">{title}</span>
        <span className="t-body-medium sn-notifications__help">{help}</span>
      </span>
      <Switch checked={checked} onCheckedChange={onChange} />
    </label>
  );
}

function ExpiredModes({
  prefs,
  onPick,
}: {
  prefs: NotificationPrefs;
  onPick: (mode: NotificationPrefs['expiredMode']) => void;
}) {
  const id = useId();
  return (
    <>
      <h2 className="t-title-medium" id={id}>
        {str('notif_expired')}
      </h2>
      <div role="radiogroup" aria-labelledby={id} className="sn-notifications__choices">
        {EXPIRED_MODES.map((mode) => (
          <label key={mode} className="sn-notifications__choice t-body-large">
            <Radio name={id} checked={prefs.expiredMode === mode} onCheckedChange={() => onPick(mode)} />
            {str(`expired_${mode}` as StringId)}
          </label>
        ))}
      </div>
    </>
  );
}
