import { useEffect, useMemo, useState } from 'react';
import { combineLatest, map } from 'rxjs';
import { messageId } from '../../core/errors';
import { useAction, useObservable } from '../../core/hooks';
import { plural, str } from '../../core/i18n';
import { useHouseholdSession, usePreferences, useServices } from '../../core/services';
import { NotificationPrompt, NotificationRules } from '../../domain/notifications';
import { ExportFormat } from '../../domain/settings/export';
import { today } from '../../domain/time';
import { ActionRow, Button, Dialog, ErrorText, HighlightCard, LeafBadge, ListGroup, useSnackbars } from '../kit';
import { PasswordField } from '../forms';
import { DetailScaffold } from '../Scaffold';
import { formatHour, useNotificationPrefs, usePushStatus } from '../notifications/reminders';
import { AboutGroup, AppearanceCard, DataSection } from './SettingsSections';
import { useDeleteAccount } from './DeleteAccount';
import { UnsyncedSignOutDialog } from '../household/UnsyncedSignOut';
import '../household/household.css';
import './settings.css';

/** The steps of Clear household data: an "are you sure" step, then the owner's password. */
type ClearStep = 'none' | 'areYouSure' | 'password';

/** Saves [text] as a download: Safari asks, then keeps it in Files > Downloads (the app's Save dialog). */
function download(name: string, text: string) {
  const url = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/**
 * Settings (DESIGN 5.6, 19.12) in the 2026-11 look: the account as the highlight card, Household and Notifications,
 * Appearance, Data and About as groups of rows, then Sign out as a full-width tonal button, Delete account and, for the
 * owner, Clear household data.
 */
export function SettingsScreen({
  onBack,
  onOpenHousehold,
  onOpenNotifications,
  onOpenAbout,
}: {
  onBack: () => void;
  onOpenHousehold: () => void;
  onOpenNotifications: () => void;
  onOpenAbout: () => void;
}) {
  const me = useHouseholdSession();
  const { session, auth, users, households, exporter, preferences, clock } = useServices();
  const { themeMode, colorTheme, weekStart } = usePreferences();
  const snackbars = useSnackbars();
  const action = useAction();
  const [verified, setVerified] = useState(me.user.emailVerified);
  const [verificationSent, setVerificationSent] = useState(false);
  const deleteAccount = useDeleteAccount();
  const [clearStep, setClearStep] = useState<ClearStep>('none');
  // Sign out asks first when changes haven't synced yet (after v1).
  const [askUnsynced, setAskUnsynced] = useState(false);
  const signOut = (evenIfUnsynced = false) =>
    void action.run(async () => {
      setAskUnsynced(false);
      if (!(await session.signOut(evenIfUnsynced))) setAskUnsynced(true);
    });
  const notifications = useNotificationPrefs();
  const blocked = NotificationPrompt.blockedHere(usePushStatus(), notifications?.enabled ?? null);
  const household = useObservable(
    useMemo(
      () =>
        combineLatest([households.observeHousehold(me.householdId), households.observeMembers(me.householdId)]).pipe(
          map(([h, members]) => (h ? { name: h.name, memberCount: members.length } : null)),
        ),
      [households, me.householdId],
    ),
    null,
  );
  // A verified email shows once the account is refreshed (reload).
  useEffect(() => {
    void auth.reload().then(
      (user) => user && setVerified(user.emailVerified),
      () => undefined,
    );
  }, [auth]);

  return (
    <DetailScaffold title={str('settings')} onBack={onBack} header="pushed" testId="screen_settings">
      <div className="sn-settings">
        <ErrorText text={clearStep === 'none' && action.error ? str(action.error) : null} />
        <AccountCard
          name={me.displayName}
          email={me.user.email ?? ''}
          verified={verified}
          verificationSent={verificationSent}
          busy={action.busy}
          onResend={() =>
            void action.run(async () => {
              await auth.sendEmailVerification();
              setVerificationSent(true);
            })
          }
        />
        <ListGroup>
          <ActionRow
            icon="round/group"
            title={str('settings_household')}
            summary={
              household
                ? plural('settings_household_summary', household.memberCount, household.name, household.memberCount)
                : undefined
            }
            summaryTestId="household_summary"
            onClick={onOpenHousehold}
            opensPage
            testId="row_household"
          />
          <ActionRow
            icon={blocked ? 'round/notifications_off' : 'round/notifications'}
            title={str('settings_notifications')}
            // "Off on this phone" when the reminder is on but this phone can't show it (after v1, review UX-02).
            summary={
              notifications &&
              (!notifications.enabled
                ? str('settings_notifications_off')
                : blocked
                  ? str('settings_notifications_blocked')
                  : str('settings_notifications_on', formatHour(NotificationRules.hour(notifications))))
            }
            onClick={onOpenNotifications}
            opensPage
            testId="row_notifications"
          />
        </ListGroup>
        <AppearanceCard
          themeMode={themeMode}
          colorTheme={colorTheme}
          units={me.units}
          weekStart={weekStart}
          onThemeMode={(mode) => preferences.set({ themeMode: mode })}
          onColorTheme={(theme) => preferences.set({ colorTheme: theme })}
          onUnits={(units) => void action.run(() => users.setUnits(me.user.uid, units))}
          onWeekStart={(choice) => preferences.set({ weekStart: choice })}
        />
        <DataSection
          busy={action.busy}
          onExport={() =>
            void action.run(async () => {
              // Read from the server first: offline this fails with "You're offline" and nothing is downloaded. The
              // failure shows as a snackbar by the button, as "Export saved" does, not at the top of the screen.
              let json: string;
              try {
                json = await exporter.export(me.householdId, me.user.uid);
              } catch (error) {
                snackbars.show(str(messageId(error)));
                return;
              }
              download(ExportFormat.fileName(today(clock)), json);
              snackbars.show(str('settings_exported'));
            })
          }
        />
        <AboutGroup onOpenAbout={onOpenAbout} />
        <Button
          variant="tonal"
          full
          large
          icon="round/logout"
          className="t-button-medium sn-settings__sign-out"
          disabled={action.busy}
          onClick={() => signOut()}
        >
          {str('action_sign_out')}
        </Button>
        <Button
          variant="text"
          danger
          full
          large
          disabled={action.busy}
          onClick={() => {
            action.clearError();
            deleteAccount.open();
          }}
        >
          <span className="t-button-medium">{str('action_delete_account')}</span>
        </Button>
        {me.role === 'owner' && (
          <Button
            variant="text"
            danger
            full
            large
            disabled={action.busy}
            onClick={() => {
              action.clearError();
              setClearStep('areYouSure');
            }}
            data-testid="button_clear_household"
          >
            <span className="t-button-medium">{str('action_clear_household')}</span>
          </Button>
        )}
      </div>
      {clearStep === 'areYouSure' && (
        <Dialog
          title={str('clear_household_title')}
          titleClassName="t-dialog-title"
          onDismiss={() => setClearStep('none')}
          actions={
            <>
              <Button variant="text" onClick={() => setClearStep('none')}>
                {str('action_cancel')}
              </Button>
              <Button variant="text" danger onClick={() => setClearStep('password')} data-testid="clear_household_yes">
                {str('clear_household_yes')}
              </Button>
            </>
          }
        >
          <p>{str('clear_household_body', household?.name ?? '')}</p>
        </Dialog>
      )}
      {clearStep === 'password' && (
        <PasswordConfirmDialog
          title={str('clear_household_password_title')}
          body={str('clear_household_password_body')}
          action={str('clear_household_confirm')}
          busy={action.busy}
          error={action.error && str(action.error)}
          onDismiss={() => {
            action.clearError();
            setClearStep('none');
          }}
          onConfirm={(password) =>
            void action.run(async () => {
              await auth.verifyPassword(password);
              const outcome = await households.resetData(me.householdId, { uid: me.user.uid, name: me.displayName });
              setClearStep('none');
              snackbars.show(str(outcome === 'cleared' ? 'clear_household_done' : 'clear_household_when_online'));
            })
          }
        />
      )}
      {askUnsynced && <UnsyncedSignOutDialog onSignOut={() => signOut(true)} onDismiss={() => setAskUnsynced(false)} />}
      {deleteAccount.dialog}
    </DetailScaffold>
  );
}

/**
 * The account as Settings' highlight card (DESIGN 19.8, 19.12): the name's initial in a leaf, the name and the email,
 * and while the email isn't verified a nested row with Resend email (under the words from 1.5× text).
 */
function AccountCard({
  name,
  email,
  verified,
  verificationSent,
  busy,
  onResend,
}: {
  name: string;
  email: string;
  verified: boolean;
  verificationSent: boolean;
  busy: boolean;
  onResend: () => void;
}) {
  const initial = name.trim().charAt(0).toUpperCase();
  return (
    <HighlightCard className="sn-account-card" data-testid="card_account">
      <div className="sn-account-card__who">
        <LeafBadge highlight size={56} letter={initial || undefined} icon="round/person" />
        <div className="sn-account-card__names">
          <span className="t-card-title">{name}</span>
          <span className="t-body-medium sn-account-card__email" data-testid="account_email">
            {email}
          </span>
        </div>
      </div>
      {!verified && (
        <ListGroup nested>
          <div className="sn-segment sn-account-card__unverified" data-testid="row_email_unverified">
            <span className="sn-row-lines">
              <span className="t-row-title">{str('email_unverified')}</span>
              {verificationSent && (
                <span className="t-row-supporting c-on-surface-variant">{str('verification_sent')}</span>
              )}
            </span>
            <Button variant="text" className="t-button-medium" disabled={busy || verificationSent} onClick={onResend}>
              {str('action_resend_verification')}
            </Button>
          </div>
        </ListGroup>
      )}
    </HighlightCard>
  );
}

/** A password field with a destructive action, for the last step of Clear household data. */
function PasswordConfirmDialog({
  title,
  body,
  action,
  busy,
  error,
  onDismiss,
  onConfirm,
}: {
  title: string;
  body: string;
  action: string;
  busy: boolean;
  error: string | null;
  onDismiss: () => void;
  onConfirm: (password: string) => void;
}) {
  const [password, setPassword] = useState('');
  return (
    <Dialog
      title={title}
      titleClassName="t-dialog-title"
      onDismiss={onDismiss}
      actions={
        <>
          <Button variant="text" onClick={onDismiss}>
            {str('action_cancel')}
          </Button>
          <Button
            variant="text"
            danger
            disabled={busy || password === ''}
            onClick={() => onConfirm(password)}
            data-testid="clear_household_confirm"
          >
            {action}
          </Button>
        </>
      }
    >
      <div className="sn-dialog-form">
        <p>{body}</p>
        <PasswordField value={password} onValueChange={setPassword} />
        <ErrorText text={error} />
      </div>
    </Dialog>
  );
}
