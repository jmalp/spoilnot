# Spoil Not

Spoil Not is an Android app for a household that shares one kitchen. It keeps track of the food you have, reminds
you before it expires, and keeps a grocery list that everyone in the home can see and add to.

I designed it and directed its construction, and [Claude Code](https://claude.com/claude-code) wrote the code. This
README covers what the app does and, in more detail, how I planned, guided and checked the work.

*Jomar Malpica*

## Why I built it

Two problems in a shared home kept coming up for me. Food got thrown away because nobody noticed it was about to
expire. And groceries were hard to coordinate: someone buys milk that is already in the fridge, or nobody buys the
eggs because everyone assumed someone else would.

I'm a software developer, and I wanted to learn agentic software engineering properly: not asking an AI for
snippets, but running a whole project with an AI agent doing the implementation while I own the product, the
architecture and the quality bar. A small app that solves a real problem at home was a good way to practice that.

## What the app does

- **Pantry.** Everything in the house, with quantity, where it is stored (fridge, freezer, pantry) and its expiry
  date. Each purchase is its own row, colored and labeled by how soon it expires.
- **Expiry reminders.** One daily notification at the time each person chooses, listing what expires in the next
  few days and what should be thrown away.
- **Shared grocery lists.** Quick add, check-off, and an "Add to pantry" step after shopping that moves everything
  into the pantry at once. Staples are added to the list automatically when they run low.
- **Recipes.** Import from a recipe website (share a link from the browser or use the built-in browser), scale
  servings, cook step by step, and send the ingredients to the grocery list with a hint for what is probably already
  at home.
- **Households.** One person creates a household and shares an invite code; the owner approves each person who asks
  to join. Changes on one phone show up on the others in about a second, and the app keeps working offline.
- **Insights.** A monthly view of how much was used versus thrown away, with charts, a streak and the reasons food
  was wasted.
- **Personalization.** Light and dark mode, Material You colors, and five preset color themes built from palettes I
  picked.

Every destructive action can be undone, and status is never shown by color alone.

## How it's built

A native Android app in Kotlin with Jetpack Compose and Material 3. Firebase handles sign-in, the shared database
(Firestore) and push notifications, all on the free Spark plan. A small TypeScript job on GitHub Actions sends the
daily reminders and makes a nightly backup. There are no paid services, no ads and no analytics.

The app is in beta with a small group of testers through Firebase App Distribution. It is Android only.

## How I built it with Claude Code

The project went from an empty repository to a tested beta in about a week, with the design work done beforehand.
Claude Code wrote the code; the planning, the decisions and the review were mine. These are the practices that made
that work.

### 1. I started with a design document, not code

Before anything was built, I worked through the whole product with Claude in a chat and turned it into a single
specification: [docs/DESIGN.md](docs/DESIGN.md). I treated it the way I would a spec for a development team. It
covers:

- the features, screen by screen, with wireframes and empty, error, offline and quota states;
- the data model, sync behavior, security rules and a rules test matrix;
- a budget for the free Firebase quota, with the math to back it up;
- performance budgets, accessibility requirements and a manual QA script;
- the steps that only I could do (creating accounts, keys and signing certificates), listed up front;
- eight milestones (M0 to M7), each with acceptance criteria;
- requirement levels (MUST, SHOULD, MAY), so the agent knew what was negotiable.

Vendor facts such as quotas, pricing and library versions were checked before the build started and recorded in the
document with their sources, so the agent never relied on guesses about cost or limits.

### 2. I turned the plan into rules the agent follows every time

[CLAUDE.md](CLAUDE.md) holds the rules Claude Code reads at the start of every session. A few examples:

- Composables and ViewModels never touch Firebase; only data repositories do. Business logic is pure Kotlin with an
  injectable clock and unit tests.
- Never add Cloud Functions or any paid Google Cloud service. Never commit secrets.
- Every destructive action has Undo, and status is never conveyed by color alone.
- The exact commands to run before a task counts as finished: formatting, lint, unit tests, both build flavors,
  the security rules tests, screenshot tests and the instrumented tests on an emulator.

When something needed to hold in every later session, it went into this file. One example is my rule that every
change also updates the design document.

### 3. I built it in milestones, and nothing moved forward until the last one passed

Each milestone started the same way: Claude Code listed what it needed from me, I did those steps (Firebase
projects, the upload keystore, service accounts, console settings), and only then did it start.

Each milestone ended with its acceptance checks and a summary, which I reviewed before the next one began. Every
milestone was its own pull request, and nothing merged until CI passed and I approved it. Two-phone scenarios were tested against the Firebase emulators, such as a
second person joining with a code or a removed member losing access within five seconds.

### 4. I kept the decisions that matter with me

Anything with real-world consequences needed my approval in the moment, every time:

- deploying security rules to the live projects;
- sending a build or an invite to testers;
- anything involving keys or personal data. Tester emails live in a git-ignored file, and keystore passwords never
  appear in a session.

I also made the product calls myself:

- **App Check.** I skipped it because it requires a paid Play Console account, and recorded why.
- **Icon.** I picked the launcher icon from three proposals.
- **Order of work.** After v1, I set the order of the next features.
- **Cuts.** I dropped a planned feature, a notification for join requests, when it no longer seemed worth it.

### 5. I tested with real people and fed back what I saw

The app went to testers in four betas, and their feedback produced concrete changes:

- one row per purchase in the pantry instead of expandable groups;
- a single form for adding and editing items;
- a "Cooked" category for leftovers;
- form sheets that close only after a deliberate drag past halfway;
- better button contrast on the Home cards;
- a fix for a sheet that kept shaking after a flick.

For larger requests, I had Claude Code restate the list of changes in its own words before writing any code, then
approved it or corrected it. That one step caught misunderstandings early.

I also watched the work as it ran. During one benchmark I noticed the test was swiping on the phone's home screen
instead of inside the app. Pointing that out led straight to the bug in the test.

### 6. I held the line on quality, security and cost

- **Tests at every level.** Around 260 unit and screenshot tests, 70 security rules tests, about 30 instrumented
  tests (including two-phone flows) and integration tests for the reminder job, all running in CI.
- **A security review against the design.** Claude Code reviewed the app section by section, and I completed the
  console steps and signed it off ([docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md)). That covered restricted API
  keys, the password policy, email enumeration protection and two-factor authentication on GitHub.
- **Cost as a requirement.** A simulation replays a day of ten households against the emulator. When it showed the
  free read quota would run out at about seven households, I made cheaper syncing a priority. The change cut reads by
  about 60%, so roughly eighteen households now fit.
- **Operations.** A one-command release script that refuses to upload unless the build is signed with the right key,
  nightly exports with a restore workflow, secret scanning and dependency updates. All of it is documented in
  [docs/RUNBOOK.md](docs/RUNBOOK.md).

### 7. Every change leaves a record

Every deviation from the design goes in [docs/DECISIONS.md](docs/DECISIONS.md), with the date, the decision and the
reason. After v1, I made the design document a living one: every change updates the sections it affects and adds a
line to a change log at the end, in the same pull request as the code and its tests. Anyone can trace why the app is
the way it is.

## What I took from it

- **The spec does the heavy lifting.** The more precise the design document and its acceptance criteria were, the
  less I had to correct later.
- **The agent needs firm boundaries.** It works best with clear rules on architecture, cost and security, and a
  person deciding anything that touches real users, real data or real money.
- **Small checkpoints beat one big review.** Milestones, acceptance checks and beta rounds gave me regular points to
  steer instead of one large reveal at the end.
- **Owning the product is still the job.** Most of my time went into deciding what to build, testing it with people
  and reviewing what came back. Writing code was the smallest part.

## Documentation

| Document | What it covers |
| --- | --- |
| [docs/DESIGN.md](docs/DESIGN.md) | The full product and technical design, kept current, with a log of changes since v1 |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Every decision and deviation, with its reason |
| [docs/RUNBOOK.md](docs/RUNBOOK.md) | Releases, backups and restores, quota checks and other operations |
| [docs/QA.md](docs/QA.md) | The manual test script for each release |
| [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md) | The security review and its sign-off |
| [docs/PRIVACY.md](docs/PRIVACY.md) | What data the app keeps and why |
| [CLAUDE.md](CLAUDE.md) | The working rules for Claude Code |
