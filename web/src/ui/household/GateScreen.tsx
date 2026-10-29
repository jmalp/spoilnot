import { useState } from 'react';
import { AppError } from '../../core/errors';
import { useAction } from '../../core/hooks';
import { str } from '../../core/i18n';
import { useServices } from '../../core/services';
import { HouseholdRules, InviteCodes, type InviteCode } from '../../domain/household';
import type { JoinState, Session } from '../../domain/session';
import { Button, Card, Dialog, ErrorText, IconButton, Menu, MenuItem, Progress, TextField, TopAppBar } from '../kit';
import './household.css';

type NoHousehold = Extract<Session, { kind: 'noHousehold' }>;

/**
 * Shown to a signed-in user without a household (HouseholdGateScreen, DESIGN 5.1): create one, or join one with a
 * code and the owner's approval. Nothing else is reachable until they have one.
 */
export function GateScreen({ session }: { session: NoHousehold }) {
  const { session: manager, households } = useServices();
  const create = useAction();
  const join = useAction();
  const request = useAction();
  const [menuOpen, setMenuOpen] = useState(false);
  const [confirm, setConfirm] = useState<InviteCode | null>(null);
  const busy = create.busy || join.busy || request.busy;
  const uid = session.user.uid;

  const lookUp = (input: string) =>
    join.run(async () => {
      const code = InviteCodes.normalize(input);
      if (!InviteCodes.isWellFormed(code)) throw new AppError('inviteCodeInactive');
      const invite = await households.findInviteCode(code);
      if (!invite?.active) throw new AppError('inviteCodeInactive');
      setConfirm(invite);
    });

  return (
    <div className="sn-gate" data-testid="screen_gate">
      <TopAppBar
        title={str('gate_title', session.displayName)}
        actions={
          <span className="sn-anchor">
            <IconButton icon="round/more_vert" label={str('more_options')} onClick={() => setMenuOpen(true)} />
            <Menu open={menuOpen} onDismiss={() => setMenuOpen(false)}>
              <MenuItem
                label={str('action_sign_out')}
                onClick={() => {
                  setMenuOpen(false);
                  void create.run(() => manager.signOut());
                }}
              />
            </Menu>
          </span>
        }
      />
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
                      await households.requestToJoin(uid, session.displayName, confirm);
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
          {str('join_confirm_body', confirm.householdName)}
        </Dialog>
      )}
    </div>
  );
}

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
    <Card variant="filled" data-testid={testId}>
      <div className="sn-gate-card">
        <h2 className="t-title-large">{title}</h2>
        <p className="t-body-medium c-on-surface-variant">{body}</p>
        {children}
      </div>
    </Card>
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
        <Button type="submit" full large busy={busy}>
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
        <Button type="submit" variant="outlined" full large disabled={busy || code.length !== InviteCodes.LENGTH}>
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
  return (
    <GateCard title={str(title)} body={str(body, join.pending.householdName)} testId="card_join_status">
      {join.kind === 'waiting' && (
        <div className="sn-gate-card__progress">
          <Progress size={32} />
        </div>
      )}
      <ErrorText text={error} />
      <Button variant="outlined" full large disabled={busy} onClick={onAbandon}>
        {str(action)}
      </Button>
    </GateCard>
  );
}
