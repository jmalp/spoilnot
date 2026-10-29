import { useMemo, useState } from 'react';
import { combineLatest, distinctUntilChanged, map, of, switchMap } from 'rxjs';
import { useAction, useObservable } from '../../core/hooks';
import { str } from '../../core/i18n';
import { useHouseholdSession, useServices } from '../../core/services';
import { HouseholdRules, InviteCodes, type Household, type JoinRequest, type Member } from '../../domain/household';
import {
  Button,
  Card,
  Dialog,
  ErrorText,
  IconButton,
  ListItem,
  Menu,
  MenuItem,
  Progress,
  TextField,
  useSnackbars,
} from '../kit';
import { DetailScaffold } from '../Scaffold';
import './household.css';

type Content = {
  household: Household | null;
  members: Member[];
  /** Waiting requests; always empty for members. */
  requests: JoinRequest[];
  joiningPaused: boolean;
};

type Confirm =
  | { kind: 'rename' }
  | { kind: 'regenerate' }
  | { kind: 'remove'; member: Member }
  | { kind: 'transfer'; member: Member }
  | { kind: 'leave' }
  | { kind: 'delete' };

/** Settings > Household (DESIGN 5.1): join requests, the invite card, members and the owner's actions. */
export function HouseholdSettingsScreen({ onBack }: { onBack: () => void }) {
  const me = useHouseholdSession();
  const { households } = useServices();
  const snackbars = useSnackbars();
  const action = useAction();
  const [confirm, setConfirm] = useState<Confirm | null>(null);
  const isOwner = me.role === 'owner';
  const hid = me.householdId;

  const content = useObservable<Content | null>(
    useMemo(() => {
      const household$ = households.observeHousehold(hid);
      const code$ = household$.pipe(
        map((h) => h?.inviteCode ?? ''),
        distinctUntilChanged(),
        switchMap((code) => (code ? households.observeInviteCode(code) : of(null))),
      );
      const requests$ = isOwner
        ? households.observeJoinRequests(hid).pipe(map((all) => all.filter((r) => r.status === 'pending')))
        : of([]);
      return combineLatest([household$, households.observeMembers(hid), requests$, code$]).pipe(
        map(([household, members, requests, invite]) => ({
          household,
          members,
          requests,
          joiningPaused: invite?.active === false,
        })),
      );
    }, [households, hid, isOwner]),
    null,
  );
  const household = content?.household;
  const name = household?.name ?? '';

  return (
    <DetailScaffold
      title={household?.name ?? str('settings_household')}
      onBack={onBack}
      testId="screen_household"
      actions={
        isOwner && (
          <IconButton icon="round/edit" label={str('action_rename')} onClick={() => setConfirm({ kind: 'rename' })} />
        )
      }
    >
      {!content || !household ? (
        <div className="sn-centered">
          <Progress />
        </div>
      ) : (
        <div className="sn-stack">
          <ErrorText text={action.error && str(action.error)} />
          {isOwner && content.requests.length > 0 && (
            <JoinRequestsCard
              requests={content.requests}
              busy={action.busy}
              onApprove={(r) => void action.run(() => households.approve(hid, r))}
              onDecline={(r) => void action.run(() => households.decline(hid, r.uid))}
            />
          )}
          <InviteCard
            household={household}
            isOwner={isOwner}
            paused={content.joiningPaused}
            busy={action.busy}
            onCopied={() => snackbars.show(str('code_copied'))}
            onRegenerate={() => setConfirm({ kind: 'regenerate' })}
            onPause={(paused) => void action.run(() => households.setJoiningPaused(household.inviteCode, paused))}
          />
          <MembersCard
            members={content.members}
            myUid={me.user.uid}
            isOwner={isOwner}
            onRemove={(member) => setConfirm({ kind: 'remove', member })}
            onMakeOwner={(member) => setConfirm({ kind: 'transfer', member })}
          />
          {isOwner ? (
            <>
              <p className="t-body-medium c-on-surface-variant">{str('owner_cannot_leave')}</p>
              <DangerButton
                label={str('action_delete_household')}
                busy={action.busy}
                onClick={() => setConfirm({ kind: 'delete' })}
              />
            </>
          ) : (
            <DangerButton
              label={str('action_leave')}
              busy={action.busy}
              onClick={() => setConfirm({ kind: 'leave' })}
            />
          )}
        </div>
      )}
      {household && confirm && (
        <ConfirmFor
          confirm={confirm}
          name={name}
          onDismiss={() => setConfirm(null)}
          onRename={(newName) => void action.run(() => households.rename(household, newName))}
          onConfirm={() => {
            const uid = me.user.uid;
            switch (confirm.kind) {
              case 'regenerate':
                return void action.run(() =>
                  households.regenerateInviteCode(household, uid, InviteCodes.generate(), !content?.joiningPaused),
                );
              case 'remove':
                return void action.run(() => households.removeMember(hid, confirm.member.uid));
              case 'transfer':
                return void action.run(() => households.transferOwnership(hid, uid, confirm.member.uid));
              case 'leave':
                return void action.run(() => households.leave(hid, uid));
              case 'delete':
                return void action.run(() => households.deleteHousehold(household, uid));
              case 'rename':
                return;
            }
          }}
        />
      )}
    </DetailScaffold>
  );
}

function SectionCard({ title, testId, children }: { title: string; testId: string; children: React.ReactNode }) {
  return (
    <Card data-testid={testId}>
      <div className="sn-section">
        <h2 className="t-title-medium sn-section__title">{title}</h2>
        {children}
      </div>
    </Card>
  );
}

function JoinRequestsCard({
  requests,
  busy,
  onApprove,
  onDecline,
}: {
  requests: JoinRequest[];
  busy: boolean;
  onApprove: (r: JoinRequest) => void;
  onDecline: (r: JoinRequest) => void;
}) {
  return (
    <SectionCard title={str('join_requests_title')} testId="card_join_requests">
      {requests.map((request) => (
        <div key={request.uid} className="sn-section__body">
          <p className="t-title-medium">{request.displayName}</p>
          <div className="sn-row">
            <Button variant="tonal" large disabled={busy} onClick={() => onApprove(request)}>
              {str('action_approve')}
            </Button>
            <Button variant="outlined" large disabled={busy} onClick={() => onDecline(request)}>
              {str('action_decline')}
            </Button>
          </div>
        </div>
      ))}
    </SectionCard>
  );
}

function InviteCard({
  household,
  isOwner,
  paused,
  busy,
  onCopied,
  onRegenerate,
  onPause,
}: {
  household: Household;
  isOwner: boolean;
  paused: boolean;
  busy: boolean;
  onCopied: () => void;
  onRegenerate: () => void;
  onPause: (paused: boolean) => void;
}) {
  const formatted = InviteCodes.format(household.inviteCode);
  const shareText = str('share_message', household.name, formatted);
  return (
    <SectionCard title={str('invite_title')} testId="card_invite">
      <div className="sn-section__body">
        <p className="t-body-medium c-on-surface-variant">{str('invite_body')}</p>
        <p
          className="t-display-small sn-invite-code"
          aria-label={str('invite_code_description', [...formatted].join(' '))}
          data-testid="invite_code"
        >
          {formatted}
        </p>
        {paused && <p className="t-body-medium c-error">{str('invite_paused')}</p>}
        <div className="sn-row">
          <Button
            variant="tonal"
            large
            icon="round/content_copy"
            onClick={() => void navigator.clipboard.writeText(formatted).then(onCopied, () => undefined)}
          >
            {str('action_copy')}
          </Button>
          <Button
            variant="tonal"
            large
            icon="round/share"
            onClick={() => {
              // The iPhone's share sheet; browsers without one copy the message instead.
              if (navigator.share) void navigator.share({ text: shareText }).catch(() => undefined);
              else void navigator.clipboard.writeText(shareText).then(onCopied, () => undefined);
            }}
          >
            {str('action_share')}
          </Button>
        </div>
        {isOwner && (
          <div className="sn-row">
            <Button variant="text" large disabled={busy} onClick={onRegenerate}>
              {str('action_regenerate')}
            </Button>
            <Button variant="text" large disabled={busy} onClick={() => onPause(!paused)}>
              {str(paused ? 'action_resume_joining' : 'action_pause_joining')}
            </Button>
          </div>
        )}
      </div>
    </SectionCard>
  );
}

const joinedFormat = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' });

function MembersCard({
  members,
  myUid,
  isOwner,
  onRemove,
  onMakeOwner,
}: {
  members: Member[];
  myUid: string;
  isOwner: boolean;
  onRemove: (m: Member) => void;
  onMakeOwner: (m: Member) => void;
}) {
  return (
    <SectionCard title={str('household_members')} testId="card_members">
      {members.map((member, index) => {
        const isMe = member.uid === myUid;
        const role = str(member.role === 'owner' ? 'role_owner' : 'role_member');
        return (
          <div key={member.uid}>
            {index > 0 && <hr className="sn-divider" />}
            <ListItem
              headline={isMe ? str('member_you', member.displayName) : member.displayName}
              supporting={
                <span data-testid="member_since">
                  {member.joinedAt ? str('member_summary', role, joinedFormat.format(member.joinedAt)) : role}
                </span>
              }
              trailing={
                isOwner && !isMe ? (
                  <MemberMenu member={member} onRemove={onRemove} onMakeOwner={onMakeOwner} />
                ) : undefined
              }
            />
          </div>
        );
      })}
    </SectionCard>
  );
}

function MemberMenu({
  member,
  onRemove,
  onMakeOwner,
}: {
  member: Member;
  onRemove: (m: Member) => void;
  onMakeOwner: (m: Member) => void;
}) {
  const [open, setOpen] = useState(false);
  return (
    <span className="sn-anchor">
      <IconButton
        icon="round/more_vert"
        label={str('member_options', member.displayName)}
        onClick={() => setOpen(true)}
      />
      <Menu open={open} onDismiss={() => setOpen(false)}>
        <MenuItem
          label={str('action_make_owner')}
          onClick={() => {
            setOpen(false);
            onMakeOwner(member);
          }}
        />
        <MenuItem
          label={str('action_remove_member')}
          onClick={() => {
            setOpen(false);
            onRemove(member);
          }}
        />
      </Menu>
    </span>
  );
}

function DangerButton({ label, busy, onClick }: { label: string; busy: boolean; onClick: () => void }) {
  return (
    <Button variant="outlined" danger full large disabled={busy} onClick={onClick}>
      {label}
    </Button>
  );
}

function ConfirmFor({
  confirm,
  name,
  onDismiss,
  onRename,
  onConfirm,
}: {
  confirm: Confirm;
  name: string;
  onDismiss: () => void;
  onRename: (name: string) => void;
  onConfirm: () => void;
}) {
  if (confirm.kind === 'rename')
    return <RenameDialog current={name} onDismiss={onDismiss} onSave={(n) => (onRename(n), onDismiss())} />;
  const [title, body, label] = (() => {
    switch (confirm.kind) {
      case 'regenerate':
        return [str('regenerate_title'), str('regenerate_body'), str('action_regenerate')];
      case 'remove':
        return [
          str('remove_member_title', confirm.member.displayName),
          str('remove_member_body', confirm.member.displayName),
          str('action_remove'),
        ];
      case 'transfer':
        return [
          str('transfer_title', confirm.member.displayName),
          str('transfer_body', confirm.member.displayName),
          str('action_make_owner'),
        ];
      case 'leave':
        return [str('leave_title', name), str('leave_body'), str('action_leave_confirm')];
      case 'delete':
        return [str('delete_household_title', name), str('delete_household_body'), str('action_delete')];
    }
  })();
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
            data-testid="confirm"
            onClick={() => {
              onConfirm();
              onDismiss();
            }}
          >
            {label}
          </Button>
        </>
      }
    >
      {body}
    </Dialog>
  );
}

function RenameDialog({
  current,
  onDismiss,
  onSave,
}: {
  current: string;
  onDismiss: () => void;
  onSave: (name: string) => void;
}) {
  const [name, setName] = useState(current);
  const valid = HouseholdRules.isNameValid(name);
  return (
    <Dialog
      title={str('rename_title')}
      onDismiss={onDismiss}
      actions={
        <>
          <Button variant="text" onClick={onDismiss}>
            {str('action_cancel')}
          </Button>
          <Button variant="text" disabled={!valid} onClick={() => onSave(name)}>
            {str('action_save')}
          </Button>
        </>
      }
    >
      <div className="sn-dialog-form">
        <TextField
          label={str('field_household_name')}
          value={name}
          onValueChange={setName}
          error={valid ? undefined : str('error_household_name')}
        />
      </div>
    </Dialog>
  );
}
