import { useMemo, useState } from 'react';
import { combineLatest, map, of } from 'rxjs';
import { useAction, useNow, useObservable } from '../../core/hooks';
import { str } from '../../core/i18n';
import { useHouseholdSession, useServices } from '../../core/services';
import { HouseholdRules, InviteCodes, type Household, type JoinRequest, type Member } from '../../domain/household';
import {
  Button,
  Dialog,
  ErrorText,
  GroupHeading,
  HighlightCard,
  IconButton,
  IconTile,
  ListGroup,
  Menu,
  MenuItem,
  PlainCard,
  Progress,
  RoundIconButton,
  TextField,
  useSnackbars,
} from '../kit';
import { DetailScaffold } from '../Scaffold';
import { relativeTime } from '../home/labels';
import './household.css';

type Content = {
  household: Household | null;
  members: Member[];
  /** Waiting requests; always empty for members. */
  requests: JoinRequest[];
};

type Confirm =
  | { kind: 'rename' }
  | { kind: 'regenerate' }
  | { kind: 'remove'; member: Member }
  | { kind: 'transfer'; member: Member }
  | { kind: 'leave' }
  | { kind: 'delete' };

/**
 * Settings > Household (DESIGN 5.1, 19): a pushed screen titled with the household's name, Rename beside Back for the
 * owner; join requests as the highlight card, the invite on a plain card, the members as a group of rows, then Delete
 * household or Leave household.
 */
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
      const requests$ = isOwner
        ? households.observeJoinRequests(hid).pipe(map((all) => all.filter((r) => r.status === 'pending')))
        : of([]);
      return combineLatest([household$, households.observeMembers(hid), requests$]).pipe(
        map(([household, members, requests]) => ({ household, members, requests })),
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
      header="pushed"
      testId="screen_household"
      actions={
        isOwner && (
          <RoundIconButton
            icon="round/edit"
            label={str('action_rename')}
            onClick={() => setConfirm({ kind: 'rename' })}
          />
        )
      }
    >
      {!content || !household ? (
        <div className="sn-centered">
          <Progress />
        </div>
      ) : (
        <div className="sn-household">
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
            busy={action.busy}
            onCopied={() => snackbars.show(str('code_copied'))}
            onRegenerate={() => setConfirm({ kind: 'regenerate' })}
          />
          <MembersGroup
            members={content.members}
            myUid={me.user.uid}
            isOwner={isOwner}
            onRemove={(member) => setConfirm({ kind: 'remove', member })}
            onMakeOwner={(member) => setConfirm({ kind: 'transfer', member })}
          />
          {isOwner ? (
            <>
              <p className="t-body-medium c-on-surface-variant sn-household__note">{str('owner_cannot_leave')}</p>
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
                return void action.run(() => households.regenerateInviteCode(household, uid, InviteCodes.generate()));
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

/**
 * The owner's waiting join requests as the highlight card (DESIGN 19.8): each request a nested row with who is asking
 * (their masked sign-in address) and when, then Approve (filled) and Decline (tonal).
 */
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
  const { clock } = useServices();
  const now = useNow(clock);
  return (
    <HighlightCard className="sn-join-requests" data-testid="card_join_requests">
      <div className="sn-join-requests__head">
        <h2 className="t-card-title">{str('join_requests_title')}</h2>
        {/* After v1: who is asking (their masked sign-in address) and when, not only the name they typed. Requests
            from builds before this have neither field. */}
        <p className="t-body-medium">{str('join_requests_hint')}</p>
      </div>
      <ListGroup nested>
        {requests.map((request) => (
          <div key={request.uid} className="sn-segment sn-join-request" data-testid={`join_request_${request.uid}`}>
            <div className="sn-join-request__who">
              <span className="t-row-title">{request.displayName}</span>
              {request.emailMasked && (
                <span className="t-row-supporting c-on-surface-variant">{request.emailMasked}</span>
              )}
              {request.requestedAt && (
                <span className="t-row-supporting c-on-surface-variant">
                  {str('join_request_sent', relativeTime(request.requestedAt.getTime(), now))}
                </span>
              )}
            </div>
            <div className="sn-row">
              {/* "Approve Robin Lee": with two requests the buttons name whom they answer (after v1, review A11Y-08). */}
              <Button
                large
                className="t-button-medium"
                disabled={busy}
                aria-label={str('join_request_approve_description', request.displayName)}
                onClick={() => onApprove(request)}
              >
                {str('action_approve')}
              </Button>
              <Button
                variant="tonal"
                large
                className="t-button-medium"
                disabled={busy}
                aria-label={str('join_request_decline_description', request.displayName)}
                onClick={() => onDecline(request)}
              >
                {str('action_decline')}
              </Button>
            </div>
          </div>
        ))}
      </ListGroup>
    </HighlightCard>
  );
}

/**
 * Invite people on a plain card: what to do with the code, the code itself large (read character by character), Copy
 * and Share as tonal buttons, and New code for the owner.
 */
function InviteCard({
  household,
  isOwner,
  busy,
  onCopied,
  onRegenerate,
}: {
  household: Household;
  isOwner: boolean;
  busy: boolean;
  onCopied: () => void;
  onRegenerate: () => void;
}) {
  const formatted = InviteCodes.format(household.inviteCode);
  const shareText = str('share_message', household.name, formatted);
  return (
    <PlainCard className="sn-invite" data-testid="card_invite">
      <h2 className="t-section-title">{str('invite_title')}</h2>
      <p className="t-body-medium c-on-surface-variant">{str('invite_body')}</p>
      <p
        className="t-accent-number c-primary sn-invite-code"
        aria-label={str('invite_code_description', [...formatted].join(' '))}
        data-testid="invite_code"
      >
        {formatted}
      </p>
      <div className="sn-row">
        <Button
          variant="tonal"
          large
          icon="round/content_copy"
          className="t-button-medium"
          onClick={() => void navigator.clipboard.writeText(formatted).then(onCopied, () => undefined)}
        >
          {str('action_copy')}
        </Button>
        <Button
          variant="tonal"
          large
          icon="round/share"
          className="t-button-medium"
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
          <Button variant="text" large className="t-button-medium" disabled={busy} onClick={onRegenerate}>
            {str('action_regenerate')}
          </Button>
        </div>
      )}
    </PlainCard>
  );
}

const joinedFormat = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' });

/**
 * The members under their group heading, a row each: the name's initial on a tile, the name (with "(you)"), the role
 * and when they joined, and for the owner a ⋮ with Make owner and Remove on everyone else's row.
 */
function MembersGroup({
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
    <section className="sn-household__section" data-testid="card_members">
      <GroupHeading text={str('household_members')} />
      <ListGroup>
        {members.map((member) => {
          const isMe = member.uid === myUid;
          const role = str(member.role === 'owner' ? 'role_owner' : 'role_member');
          return (
            <div key={member.uid} className="sn-segment sn-member">
              <IconTile letter={member.displayName.trim().charAt(0).toUpperCase()} />
              <span className="sn-row-lines">
                <span className="t-row-title">{isMe ? str('member_you', member.displayName) : member.displayName}</span>
                <span className="t-row-supporting c-on-surface-variant" data-testid="member_since">
                  {member.joinedAt ? str('member_summary', role, joinedFormat.format(member.joinedAt)) : role}
                </span>
              </span>
              {isOwner && !isMe && <MemberMenu member={member} onRemove={onRemove} onMakeOwner={onMakeOwner} />}
            </div>
          );
        })}
      </ListGroup>
    </section>
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

/** Delete household or Leave household: a text button in the error color, the width of the page. */
function DangerButton({ label, busy, onClick }: { label: string; busy: boolean; onClick: () => void }) {
  return (
    <Button variant="text" danger full large className="t-button-medium" disabled={busy} onClick={onClick}>
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
      titleClassName="t-dialog-title"
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
      titleClassName="t-dialog-title"
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
