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
// GitHub's own errors (a 5xx, rate limiting, no answer) usually pass within a minute or two, and the script tries
// again every minute: it emails about them only when a quarter hour's run still hasn't started this many minutes in.
const QUIET_MINUTES = 10;
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

/** Minutes since the quarter hour that `now` falls in began: 09:07:30 gives 7. */
function minutesIntoSlot(now) {
  return Math.floor(now.getTime() / 60000) - slotOf(now);
}

/** GitHub's own trouble, which passes by itself: a server error, rate limiting, or no answer at all (0). */
function isGitHubSide(code) {
  return code === 0 || code === 429 || code >= 500;
}

/**
 * What to email the owner when GitHub didn't start the run, or null to keep trying quietly for now. `kind` keeps the
 * two once-a-day limits apart, so a day's warning about GitHub's side never hides a token that stops working later.
 */
function warningFor(code, now) {
  const late = '\n\nUntil this is fixed, the reminders go out only on GitHub\'s own schedule, often hours late.';
  if (code === 401) {
    return {
      kind: 'token',
      subject: 'Spoil Not: reminders are not starting on time',
      body:
        'GitHub refused the token (401): it has expired or was revoked. Make a new one and replace the script ' +
        'property GITHUB_TOKEN (docs/RUNBOOK.md, "On-time reminders", Renew the token).' +
        late,
    };
  }
  if (code === 403 || code === 404) {
    return {
      kind: 'token',
      subject: 'Spoil Not: reminders are not starting on time',
      body:
        'GitHub answered ' + code + ' when starting the reminder job: the token no longer has access to ' + REPO +
        ' or to its Actions. Check its repository access and "Actions: Read and write" (docs/RUNBOOK.md, ' +
        '"On-time reminders").' +
        late,
    };
  }
  if (isGitHubSide(code)) {
    if (minutesIntoSlot(now) < QUIET_MINUTES) return null;
    const since = new Date(slotOf(now) * 60000).toISOString().slice(11, 16) + ' UTC';
    const answer = code === 0 ? 'GitHub could not be reached' : 'GitHub has answered ' + code;
    return {
      kind: 'github',
      subject: 'Spoil Not: GitHub is slow to start the reminders',
      body:
        answer + ' at every try since ' + since + ', so that quarter hour\'s reminder run hasn\'t started yet. ' +
        'This is on GitHub\'s side (https://www.githubstatus.com), not the token: the script keeps trying every ' +
        'minute and the run starts as soon as GitHub accepts it. Nothing to do unless it happens every day.',
    };
  }
  return {
    kind: 'token',
    subject: 'Spoil Not: reminders are not starting on time',
    body:
      'GitHub answered ' + code + ' when starting the reminder job, so it refused the request itself: check that ' +
      WORKFLOW + ' on main can still be started by hand with its dry_run input (docs/RUNBOOK.md, ' +
      '"On-time reminders").' +
      late,
  };
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
    warnOnceADay(props, now, {
      kind: 'token',
      subject: 'Spoil Not: reminders are not starting on time',
      body: 'The script property GITHUB_TOKEN is missing, so the reminder job is not started on time.',
    });
    return;
  }
  let code;
  try {
    code = UrlFetchApp.fetch(API + '/dispatches', dispatchRequest(token)).getResponseCode();
  } catch (e) {
    code = 0; // No answer (a timeout or a network error): GitHub's side, like a 5xx.
  }
  if (code === 204) {
    props.setProperty('lastSlot', String(slotOf(now)));
    return;
  }
  // Not recorded, so the next minute tries again. Meanwhile GitHub's own schedule keeps the reminders going, late.
  const warning = warningFor(code, now);
  if (warning) warnOnceADay(props, now, warning);
}

/** Emails the script's owner about a problem, at most once a day for each kind of problem. */
function warnOnceADay(props, now, warning) {
  const day = now.toISOString().slice(0, 10);
  const key = 'lastWarnDay_' + warning.kind;
  console.warn(warning.body);
  if (props.getProperty(key) === day) return;
  props.setProperty(key, day);
  MailApp.sendEmail(Session.getEffectiveUser().getEmail(), warning.subject, warning.body);
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
