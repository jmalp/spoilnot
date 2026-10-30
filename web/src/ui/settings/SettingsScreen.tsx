import { useEffect, useMemo, useState } from 'react';
import { combineLatest, map } from 'rxjs';
import { useAction, useObservable } from '../../core/hooks';
import { plural, str } from '../../core/i18n';
import { useHouseholdSession, usePreferences, useServices } from '../../core/services';
import { ExportFormat } from '../../domain/settings/export';
import { today } from '../../domain/time';
import { Button, Card, Dialog, ErrorText, Icon, ListItem, useSnackbars } from '../kit';
import { PasswordField } from '../forms';
import { DetailScaffold } from '../Scaffold';
import { AppearanceCard, DataCard, TourAndAboutCard } from './SettingsSections';
import '../household/household.css';

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
 * Settings (DESIGN 5.6): the account, the household, appearance and units, export, the app tour and About, signing
 * out, deleting the account and, for the owner, clearing the household's data. Notifications come with W6b.
 */
export function SettingsScreen({
  onBack,
  onOpenHousehold,
  onOpenAbout,
}: {
  onBack: () => void;
  onOpenHousehold: () => void;
  onOpenAbout: () => void;
}) {
  const me = useHouseholdSession();
  const { session, auth, users, households, exporter, preferences, clock } = useServices();
  const { themeMode, colorTheme } = usePreferences();
  const snackbars = useSnackbars();
  const action = useAction();
  const [verified, setVerified] = useState(me.user.emailVerified);
  const [verificationSent, setVerificationSent] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [clearStep, setClearStep] = useState<ClearStep>('none');
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
    <DetailScaffold title={str('settings')} onBack={onBack} testId="screen_settings">
      <div className="sn-stack">
        <ErrorText text={!confirmDelete && clearStep === 'none' && action.error ? str(action.error) : null} />
        <Card>
          <h2 className="t-title-medium sn-settings__card-title">{str('settings_account')}</h2>
          <ListItem
            headline={me.displayName}
            supporting={<span data-testid="account_email">{me.user.email ?? ''}</span>}
            leading={<Icon name="round/person" />}
          />
          {!verified && (
            <ListItem
              headline={str('email_unverified')}
              supporting={verificationSent ? str('verification_sent') : undefined}
              trailing={
                <Button
                  variant="text"
                  disabled={action.busy || verificationSent}
                  onClick={() =>
                    void action.run(async () => {
                      await auth.sendEmailVerification();
                      setVerificationSent(true);
                    })
                  }
                >
                  {str('action_resend_verification')}
                </Button>
              }
            />
          )}
        </Card>
        <Card>
          <ListItem
            headline={str('settings_household')}
            supporting={
              household
                ? plural('settings_household_summary', household.memberCount, household.name, household.memberCount)
                : undefined
            }
            leading={<Icon name="round/group" />}
            trailing={<Icon name="round/keyboard_arrow_right" />}
            onClick={onOpenHousehold}
            testId="row_household"
          />
        </Card>
        <AppearanceCard
          themeMode={themeMode}
          colorTheme={colorTheme}
          units={me.units}
          onThemeMode={(mode) => preferences.set({ themeMode: mode })}
          onColorTheme={(theme) => preferences.set({ colorTheme: theme })}
          onUnits={(units) => void action.run(() => users.setUnits(me.user.uid, units))}
        />
        <DataCard
          busy={action.busy}
          onExport={() =>
            void action.run(async () => {
              download(ExportFormat.fileName(today(clock)), await exporter.export(me.householdId));
              snackbars.show(str('settings_exported'));
            })
          }
        />
        {/* Starts the tour again (DESIGN 5.8); the shell picks it up. */}
        <TourAndAboutCard onTakeTour={() => preferences.set({ tourDone: false })} onOpenAbout={onOpenAbout} />
        <Button
          variant="outlined"
          full
          large
          disabled={action.busy}
          onClick={() => void action.run(() => session.signOut())}
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
            setConfirmDelete(true);
          }}
        >
          {str('action_delete_account')}
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
            {str('action_clear_household')}
          </Button>
        )}
      </div>
      {clearStep === 'areYouSure' && (
        <Dialog
          title={str('clear_household_title')}
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
            void action
              .run(async () => {
                await auth.verifyPassword(password);
                await households.resetData(me.householdId, { uid: me.user.uid, name: me.displayName });
              })
              .then((done) => {
                if (!done) return;
                setClearStep('none');
                snackbars.show(str('clear_household_done'));
              })
          }
        />
      )}
      {confirmDelete && (
        <DeleteAccountDialog
          busy={action.busy}
          error={action.error && str(action.error)}
          onDismiss={() => setConfirmDelete(false)}
          onDelete={(password) => void action.run(() => session.deleteAccount(password))}
        />
      )}
    </DetailScaffold>
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
