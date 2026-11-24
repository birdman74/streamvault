# StreamVault Chat Context

Paste this file at the start of a new Claude chat session to restore project context quickly.
Keep this file updated whenever a story completes or a significant infrastructure change is made.

---

## Project Summary

StreamVault is a personal streaming library tracker built as a portfolio project demonstrating agentic development, spec-driven TDD, and multi-persona AI orchestration. Three Claude Code persona containers (PO, Dev, Test) collaborate autonomously via GitHub Actions to build the application from spec through merged PR.

- Repo: `github.com/birdman74/streamvault`
- Framework: `github.com/birdman74/ai-dev-toolkit`
- Stack: Java 25 / Spring Boot 3.5.x backend, Next.js/TypeScript frontend, PostgreSQL + MongoDB Atlas, AWS EC2
- Runner: self-hosted GitHub Actions runner at `/home/brian/actions-runner`
- Bot account: `briankcampbell-streamvault-bot` (`STREAMVAULT_BOT_GH_TOKEN`, classic token: PRs, verdicts, escalations)
- Queue manager token: `QUEUE_MANAGER_GH_TOKEN` (fine-grained PAT from `birdman74` for issue label operations)
- Board token: `PROJECT_BOARD_GH_TOKEN` (classic PAT from `birdman74`, `project` + `repo` scopes, for the user-owned project board)
- Pipeline knobs: `.github/pipeline-config.env`; hand-off rules: `scripts/transition.sh` (ADR-005)

---

## Story Status

| Story | Status |
|---|---|
| STORY-001 | Merged -- email/password auth |
| STORY-002 | Merged -- Google OAuth2 |
| STORY-003 | Merged -- build tooling |
| STORY-004 | Merged -- PR automation |
| STORY-005 | Merged -- account settings / rating type |
| STORY-006 | Merged -- TMDB search and browse |
| STORY-007 | Merged -- add movie from TMDB |
| STORY-008 | Merged -- add series from TMDB |
| STORY-009 | In progress -- view and filter library (PR #31) |

Active epic: Personal Streaming Library (STORY-006 through STORY-019)

---

## Current State

<!-- Update this section at the start of each session -->

- Story in progress: STORY-009
- Branch: feature/story-009-view-filter-library
- PR: #31
- Phase: <!-- Test writing tests / Dev implementing / Brian reviewing / etc. -->
- Runner status: <!-- running / stopped -->
- EC2 status: <!-- running / stopped -->

---

## Recent Infrastructure Changes

<!-- Keep last 3-5 significant changes here, oldest at bottom -->

- ADR-005 (2026-11-24): `trigger-*.yml` replaced by `scripts/transition.sh` + reusable `persona-run.yml` + six `pipeline-*.yml` adapters; all knobs in `.github/pipeline-config.env`; direct Dev-Test defective-test loop and PO clarification loop; escalations self-close and name the Manual Dispatch to resume
- Persona launchers use a unique container name per run and remove it on exit or timeout; unused `STREAMVAULT_BOT_GH_TOKEN` pass-through removed from compose files
- `sync-project-board.yml` (2026-11-24): `PROJECT_BOARD_GH_TOKEN`, live issue state, columns resolved by name, sync-all mode
- ADR-003 and ADR-004 (2026-11-07): persona outcome verification and `needs-brian` escalation; Test verdict comment is the only Dev-fix trigger
- Blocked labels reconciled automatically on story close (`sync-blocked-labels.yml`)

---

## Open Decisions / Blockers

<!-- Clear items when resolved -->

- Bot account cannot formally approve PRs it opened -- Test posts a verdict comment instead (ADR-004)

---

## Key File Locations

| What | Where |
|---|---|
| Persona CLAUDE.md files | `/mnt/e/dev/streamvault/.claude/personas/{po,dev,test}/CLAUDE.md` |
| Pipeline workflows | `/mnt/e/dev/streamvault/.github/workflows/` (`pipeline-*.yml`, `persona-run.yml`) |
| Pipeline config | `/mnt/e/dev/streamvault/.github/pipeline-config.env` |
| Hand-off rules | `/mnt/e/dev/streamvault/scripts/transition.sh` |
| Story specs | `/mnt/e/dev/streamvault/docs/specs/` |
| ADRs | `/mnt/e/dev/streamvault/docs/adr/` |
| Docker compose files | `/mnt/e/docker/claude-streamvault-{po,dev,test}/` |
| Launcher scripts | `~/bin/streamvault-{po,dev,test,po-auto,dev-auto,test-auto}.sh` |
| ai-dev-toolkit sync | `~/bin/ai-dev-toolkit-sync.sh` |
| EC2 SSH | `ubuntu@54.166.127.211`, key at `C:\Users\brian\.ssh\streamvault-key.pem` |

---

## Ways of Working

- One step at a time -- never give multiple instructions simultaneously
- No em-dashes in any output
- Correct git sequence: `add → commit → pull → push`
- StreamVault changes first, then tell the sync container to propagate to ai-dev-toolkit
- Before any solution: (1) What is the purpose? (2) Does it solve the problem long-term? (3) Is it the best approach for Brian?
- Brian pastes file content directly in VS Code -- never copy/move commands
- Brian doesn't open PRs for his own changes -- commits directly to main