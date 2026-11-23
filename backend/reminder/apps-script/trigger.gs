// Spoil Not: starts the reminder job on time (DESIGN 10.4; set up as in docs/RUNBOOK.md, "On-time reminders").
//
// GitHub starts scheduled workflows hours late or not at all, but runs a workflow started through its API at once.
// This Google Apps Script runs in the owner's Google account: a time-driven trigger calls tick() every minute, and
// tick() asks GitHub to run expiry-reminders once per quarter hour (:00, :15, :30 and :45 UTC), so a digest chosen
// for 9:00 arrives about a minute after 9:00. The workflow's own schedule stays as a backup; the job sends at most
// one digest a day per user however many runs start.
//
// The GitHub token is the script property GITHUB_TOKEN (Project Settings > Script properties), never in this file:
// a fine-grained token for this repository only, with Actions: read and write.

const REPO = 'jmalp/spoilnot';
const WORKFLOW = 'reminders.yml';
const SLOT_MINUTES = 15;
const API = 'https://api.github.com/repos/' + REPO + '/actions/workflows/' + WORKFLOW;

/** The quarter hour that `now` falls in, as whole minutes since 1970 (UTC): 09:07:30 gives the minute of 09:00. */
function slotOf(now) {
  const minutes = Math.floor(now.getTime() / 60000);
  return minutes - (minutes % SLOT_MINUTES);
}

/** A run is due once per quarter hour: in a slot after the last one started. */
function shouldStart(now, lastSlot) {
  return slotOf(now) > lastSlot;
}

/** The request that starts the job for real: on main, not a dry run (the input's default is a dry run). */
function dispatchRequest(token) {
  return {
    method: 'post',
    contentType: 'application/json',
    headers: {
      Authorization: 'Bearer ' + token,
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
    },
    payload: JSON.stringify({ ref: 'main', inputs: { dry_run: false } }),
    muteHttpExceptions: true,
  };
}

/** Called every minute by the trigger that install() creates. */
function tick() {
  const props = PropertiesService.getScriptProperties();
  const now = new Date();
  const lastSlot = Number(props.getProperty('lastSlot') || 0);
  if (!shouldStart(now, lastSlot)) return;
  const token = props.getProperty('GITHUB_TOKEN');
  if (!token) {
    warnOnceADay(props, now, 'The script property GITHUB_TOKEN is missing, so the reminder job is not started on time.');
    return;
  }
  const code = UrlFetchApp.fetch(API + '/dispatches', dispatchRequest(token)).getResponseCode();
  if (code === 204) {
    props.setProperty('lastSlot', String(slotOf(now)));
    return;
  }
  // Not recorded, so the next minute tries again. 401: the token expired or was revoked; 403 or 404: it lost access
  // to the repository or to Actions. Meanwhile GitHub's own schedule keeps the reminders going, late.
  warnOnceADay(
    props,
    now,
    'GitHub answered ' + code + ' when starting the reminder job (401: renew the token, docs/RUNBOOK.md).',
  );
}

/** Emails the script's owner about a problem, at most once a day. */
function warnOnceADay(props, now, message) {
  const day = now.toISOString().slice(0, 10);
  console.warn(message);
  if (props.getProperty('lastWarnDay') === day) return;
  props.setProperty('lastWarnDay', day);
  MailApp.sendEmail(
    Session.getEffectiveUser().getEmail(),
    'Spoil Not: reminders are not starting on time',
    message + '\n\nThe reminders still go out on GitHub\'s own schedule, hours late, until this is fixed.',
  );
}

/** Run once by hand: checks the token without starting anything, then makes the every-minute trigger. */
function install() {
  const token = PropertiesService.getScriptProperties().getProperty('GITHUB_TOKEN');
  if (!token) throw new Error('Add the script property GITHUB_TOKEN first (Project Settings > Script properties).');
  const response = UrlFetchApp.fetch(API, {
    headers: { Authorization: 'Bearer ' + token, Accept: 'application/vnd.github+json' },
    muteHttpExceptions: true,
  });
  if (response.getResponseCode() !== 200) {
    throw new Error('GitHub answered ' + response.getResponseCode() + ': check the token and its repository.');
  }
  uninstall();
  ScriptApp.newTrigger('tick').timeBased().everyMinutes(1).create();
  console.log('Installed: the reminder job starts every quarter hour from now on.');
}

/** Stops the on-time starts; GitHub's own schedule carries on. */
function uninstall() {
  for (const trigger of ScriptApp.getProjectTriggers()) {
    if (trigger.getHandlerFunction() === 'tick') ScriptApp.deleteTrigger(trigger);
  }
}
