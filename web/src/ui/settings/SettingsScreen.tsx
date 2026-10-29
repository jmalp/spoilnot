import { useEffect, useMemo, useState } from 'react';
import { combineLatest, map } from 'rxjs';
import { useAction, useObservable } from '../../core/hooks';
import { plural, str } from '../../core/i18n';
import { useHouseholdSession, useServices } from '../../core/services';
import { Button, Card, Dialog, ErrorText, Icon, ListItem } from '../kit';
import { PasswordField } from '../forms';
import { DetailScaffold } from '../Scaffold';
import '../household/household.css';

/**
 * Settings (DESIGN 5.6). W1 has the account, the household, signing out and deleting the account; notifications,
 * appearance, data and about come with W6.
 */
export function SettingsScreen({ onBack, onOpenHousehold }: { onBack: () => void; onOpenHousehold: () => void }) {
  const me = useHouseholdSession();
  const { session, auth, households } = useServices();
  const action = useAction();
  const [verified, setVerified] = useState(me.user.emailVerified);
  const [verificationSent, setVerificationSent] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
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
        <ErrorText text={!confirmDelete && action.error ? str(action.error) : null} />
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
      </div>
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
