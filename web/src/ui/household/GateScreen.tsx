import { useState } from 'react';
import { AppError } from '../../core/errors';
import { useAction } from '../../core/hooks';
import { str } from '../../core/i18n';
import { useServices } from '../../core/services';
import { HouseholdRules, InviteCodes, type InviteCode } from '../../domain/household';
import type { JoinState, Session } from '../../domain/session';
import { useDeleteAccount } from '../settings/DeleteAccount';
import {
  Button,
  Dialog,
  ErrorText,
  HighlightCard,
  Menu,
  MenuItem,
  PlainCard,
  Progress,
  RoundIconButton,
  TextField,
} from '../kit';
import { UnsyncedSignOutDialog } from './UnsyncedSignOut';
import './household.css';

type NoHousehold = Extract<Session, { kind: 'noHousehold' }>;

/**
 * Shown to a signed-in user without a household (HouseholdGateScreen, DESIGN 5.1): create one, or join one with a
 * code and the owner's approval. Nothing else is reachable until they have one. In the 2026-11 look (step 8): the
 * page's header, Create and Join on plain cards, and a request in flight as the highlight card.
 */
export function GateScreen({ session }: { session: NoHousehold }) {
  const { session: manager, households } = useServices();
  const create = useAction();
  const join = useAction();
  const request = useAction();
  const [menuOpen, setMenuOpen] = useState(false);
  const deleteAccount = useDeleteAccount();
  const [confirm, setConfirm] = useState<InviteCode | null>(null);
  const [askUnsynced, setAskUnsynced] = useState(false);
  const signOut = (evenIfUnsynced = false) =>
    void create.run(async () => {
      setAskUnsynced(false);
      if (!(await manager.signOut(evenIfUnsynced))) setAskUnsynced(true);
    });
  const busy = create.busy || join.busy || request.busy;
  const uid = session.user.uid;

  const lookUp = (input: string) =>
    join.run(async () => {
      const code = InviteCodes.normalize(input);
      if (!InviteCodes.isWellFormed(code)) throw new AppError('inviteCodeInactive');
      const invite = await households.findInviteCode(code);
      // A code's `active` is not read (after v1, R12): approval guards joining, and New code replaces a leaked one.
      if (!invite) throw new AppError('inviteCodeInactive');
      setConfirm(invite);
    });

  return (
    <div className="sn-screen sn-screen--page sn-gate" data-testid="screen_gate">
      {/* The page's header (DESIGN 19.7, step 8): "Welcome, Sam" with a round ⋮ for Sign out and Delete account. */}
      <header className="sn-screen-header">
        <div className="sn-screen-header__titles">
          <h1 className="t-screen-title sn-screen-header__title">{str('gate_title', session.displayName)}</h1>
        </div>
        <div className="sn-screen-header__actions">
          <span className="sn-anchor">
            <RoundIconButton icon="round/more_vert" label={str('more_options')} onClick={() => setMenuOpen(true)} />
            <Menu open={menuOpen} onDismiss={() => setMenuOpen(false)}>
              <MenuItem
                label={str('action_sign_out')}
                onClick={() => {
                  setMenuOpen(false);
                  signOut();
                }}
              />
              <MenuItem
                label={str('action_delete_account')}
                onClick={() => {
                  setMenuOpen(false);
                  deleteAccount.open();
                }}
              />
            </Menu>
          </span>
        </div>
      </header>
      <main className="sn-gate__content">
        {session.join === null ? (
          <>
            <p className="t-body-large c-on-surface-variant sn-gate__intro">{str('gate_intro')}</p>
            <CreateCard
              busy={busy}
              error={create.error && str(create.error)}
              onCreate={(name) =>
                void create.run(async () => {
                  await households.createHousehold(uid, session.displayName, name.trim(), InviteCodes.generate());
                })
              }
            />
            <JoinCard busy={busy} error={join.error && str(join.error)} onContinue={(code) => void lookUp(code)} />
          </>
        ) : (
          <JoinStatusCard
            join={session.join}
            busy={busy}
            error={request.error && str(request.error)}
            onAbandon={() => void request.run(() => households.abandonJoin(uid, session.join!.pending))}
          />
        )}
      </main>
      {confirm && (
        <Dialog
          title={str('join_confirm_title', confirm.householdName)}
          titleClassName="t-dialog-title"
          onDismiss={() => setConfirm(null)}
          actions={
            <>
              <Button variant="text" onClick={() => setConfirm(null)}>
                {str('action_cancel')}
              </Button>
              <Button
                variant="text"
                disabled={busy}
                data-testid="send_request"
                onClick={() =>
                  void join.run(async () => {
                    try {
                      await households.requestToJoin(uid, session.displayName, confirm, session.user.email);
                    } finally {
                      setConfirm(null);
                    }
                  })
                }
              >
                {str('action_send_request')}
              </Button>
            </>
          }
        >
          {str('join_confirm_body')}
        </Dialog>
      )}
      {askUnsynced && <UnsyncedSignOutDialog onSignOut={() => signOut(true)} onDismiss={() => setAskUnsynced(false)} />}
      {deleteAccount.dialog}
    </div>
  );
}

/** A gate card: a plain card (DESIGN 19.8) with its title, what it is for, then its field and button. */
function GateCard({
  title,
  body,
  testId,
  children,
}: {
  title: string;
  body: string;
  testId: string;
  children: React.ReactNode;
}) {
  return (
    <PlainCard className="sn-gate-card" data-testid={testId}>
      <div className="sn-gate-card__head">
        <h2 className="t-section-title">{title}</h2>
        <p className="t-body-medium c-on-surface-variant">{body}</p>
      </div>
      {children}
    </PlainCard>
  );
}

function CreateCard({
  busy,
  error,
  onCreate,
}: {
  busy: boolean;
  error: string | null;
  onCreate: (name: string) => void;
}) {
  const [name, setName] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const valid = HouseholdRules.isNameValid(name);
  const submit = () => {
    setSubmitted(true);
    if (valid) onCreate(name);
  };
  return (
    <GateCard title={str('gate_create_title')} body={str('gate_create_body')} testId="card_create">
      <form
        className="sn-gate-card__form"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
      >
        <TextField
          label={str('field_household_name')}
          value={name}
          onValueChange={setName}
          autoCapitalize="words"
          error={submitted && !valid ? str('error_household_name') : undefined}
        />
        <ErrorText text={error} />
        <Button type="submit" primary busy={busy}>
          {str('action_create')}
        </Button>
      </form>
    </GateCard>
  );
}

function JoinCard({
  busy,
  error,
  onContinue,
}: {
  busy: boolean;
  error: string | null;
  onContinue: (code: string) => void;
}) {
  const [code, setCode] = useState('');
  return (
    <GateCard title={str('gate_join_title')} body={str('gate_join_body')} testId="card_join">
      <form
        className="sn-gate-card__form"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          if (code.length === InviteCodes.LENGTH) onContinue(code);
        }}
      >
        {/* Uppercases as you type and accepts pasted codes with or without the hyphen; shown with the hyphen. */}
        <TextField
          label={str('field_invite_code')}
          value={InviteCodes.format(code)}
          onValueChange={(value) => setCode(InviteCodes.normalize(value))}
          autoCapitalize="characters"
          autoCorrect="off"
          spellCheck={false}
          className="sn-code-field"
        />
        <ErrorText text={error} />
        <Button
          type="submit"
          variant="tonal"
          full
          large
          className="t-button-medium"
          disabled={busy || code.length !== InviteCodes.LENGTH}
        >
          {str('action_continue')}
        </Button>
      </form>
    </GateCard>
  );
}

function JoinStatusCard({
  join,
  busy,
  error,
  onAbandon,
}: {
  join: JoinState;
  busy: boolean;
  error: string | null;
  onAbandon: () => void;
}) {
  const [title, body, action] = {
    waiting: ['waiting_title', 'waiting_body', 'action_cancel_request'],
    declined: ['declined_title', 'declined_body', 'action_ok'],
    expired: ['expired_title', 'expired_body', 'action_ok'],
  }[join.kind] as ['waiting_title', 'waiting_body', 'action_cancel_request'];
  // The request in flight is the one thing on the screen: the highlight card (DESIGN 19.8).
  return (
    <HighlightCard className="sn-join-status" data-testid="card_join_status">
      <div className="sn-join-status__head">
        <h2 className="t-card-title">{str(title)}</h2>
        <p className="t-body-large">{str(body, join.pending.householdName)}</p>
      </div>
      {join.kind === 'waiting' && (
        <div className="sn-gate-card__progress">
          <Progress size={32} />
        </div>
      )}
      <ErrorText text={error} />
      <Button variant="tonal" full large className="t-button-medium" disabled={busy} onClick={onAbandon}>
        {str(action)}
      </Button>
    </HighlightCard>
  );
}
