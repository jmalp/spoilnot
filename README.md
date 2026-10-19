# Spoil Not

Spoil Not is an Android app for a household that shares one kitchen. It keeps track of the food you have, reminds
you before it expires, and keeps a grocery list that everyone in the home can see and add to.

I designed it and directed its construction, and [Claude Code](https://claude.com/claude-code) wrote the code. This
README covers what the app does and, in more detail, how I planned, guided and checked the work.

## Why I built it

Two problems in a shared home kept coming up for me. Food got thrown away because nobody noticed it was about to
expire. And groceries were hard to coordinate: someone buys milk that is already in the fridge, or nobody buys the
eggs because everyone assumed someone else would.

I'm a software developer, and I wanted to learn agentic software engineering properly: not asking an AI for
snippets, but running a whole project with an AI agent doing the implementation while I own the product, the
architecture and the quality bar. A small app that solves a real problem at home was a good way to practice that.

## What I took from it

- **The spec does the heavy lifting.** The more precise the design document and its acceptance criteria were, the
  less I had to correct later.
- **The agent needs firm boundaries.** It works best with clear rules on architecture, cost and security, and a
  person deciding anything that touches real users, real data or real money.
- **Small checkpoints beat one big review.** Milestones, acceptance checks and beta rounds gave me regular points to
  steer instead of one large reveal at the end.
- **Owning the product is still the job.** Most of my time went into deciding what to build, testing it with people
  and reviewing what came back. Writing code was the smallest part.

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
Claude Code wrote the code; the planning, the decisions and the review were mine.

### 1. I started with a design document, not code

Before anything was built, I worked through the product with Claude in a chat and turned it into one specification,
[docs/DESIGN.md](docs/DESIGN.md). It defines every screen, the data model, the security rules and a budget for the
free Firebase quota, and splits the work into eight milestones with acceptance criteria. It also lists up front the
steps only I could do, such as creating accounts and signing keys.

### 2. I turned the plan into rules the agent follows every time

[CLAUDE.md](CLAUDE.md) holds the rules Claude Code reads at the start of every session. They cover the architecture
(no Firebase code in the UI layer, business logic in tested pure Kotlin), hard limits like no paid services and no
committed secrets, and the full set of checks that must pass before any task counts as done.

### 3. I built it in milestones, and nothing moved forward until the last one passed

Each milestone started with Claude Code listing what it needed from me, such as Firebase projects, keys and console
settings, and ended with its acceptance checks and a summary I reviewed. Every milestone was its own pull request,
and nothing merged until CI passed and I approved it.

### 4. I kept the decisions that matter with me

Anything with real-world consequences, like deploying security rules or sending a build to testers, needed my
approval each time, and keys and personal data never entered the repository. The product calls were mine too: which
icon to ship, what to build next, and what to cut or skip when the cost outweighed the value.

### 5. I tested with real people and fed back what I saw

Four betas went to testers, and their feedback reshaped the pantry, the add and edit forms and several smaller
interactions. For larger requests I had Claude Code restate the changes in its own words before writing any code,
which caught misunderstandings early.

### 6. I held the line on quality, security and cost

The app has about 360 automated tests across unit, screenshot, security-rule and two-phone instrumented tests, all
running in CI. I signed off a security review against the design. When a simulation showed the free quota would run
out at about seven households, I made cheaper syncing a priority, which cut reads by about 60%.

### 7. Every change leaves a record

Every deviation from the design goes in [docs/DECISIONS.md](docs/DECISIONS.md) with its reason. After v1, every
change also updates the design document in the same pull request, so anyone can trace why the app is the way it is.

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
