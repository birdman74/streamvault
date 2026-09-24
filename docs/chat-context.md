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
- Bot account: `briankcampbell-streamvault-bot` (classic token for PR automation)
- Queue manager token: `QUEUE_MANAGER_GH_TOKEN` (fine-grained PAT from `birdman74` for issue label operations)

---

## Story Status

| Story | Status |
|---|---|
| STORY-001 | Merged -- email/password auth |
| STORY-002 | Merged -- Google OAuth2 |
| STORY-003 | Merged -- build tooling |
| STORY-004 | Merged -- PR automation |
| STORY-005 | Merged -- account settings / rating type |
| STORY-006 | <!-- IN PROGRESS / MERGED --> |

Active epic: Personal Streaming Library (STORY-006 through STORY-019)

---

## Current State

<!-- Update this section at the start of each session -->

- Story in progress: STORY-006
- Branch: feature/story-006-...
- PR: #...
- Phase: <!-- Test writing tests / Dev implementing / Brian reviewing / etc. -->
- Runner status: <!-- running / stopped -->
- EC2 status: <!-- running / stopped -->

---

## Recent Infrastructure Changes

<!-- Keep last 3-5 significant changes here, oldest at bottom -->

- `sync-project-board.yml` added -- auto-syncs issue labels to project board columns via GITHUB_TOKEN GraphQL
- `trigger-test-next-story.yml` -- 30s sleep added after issue close to prevent race condition with GitHub API
- All push-triggered workflows standardized: `github.actor == 'birdman74' || github.actor == 'briankcampbell-streamvault-bot' || github.event_name == 'workflow_dispatch'`
- Test path filter changed to `**/src/test/**` and `**/__tests__/**` to cover backend and frontend
- Docker images rebuilt with project-specific WORKDIR for Claude Code memory isolation

---

## Open Decisions / Blockers

<!-- Clear items when resolved -->

- Bot account cannot formally approve PRs it opened -- Test posts a comment instead (known limitation)
- Verify `sync-project-board.yml` works correctly when issue labels change on STORY-006

---

## Key File Locations

| What | Where |
|---|---|
| Persona CLAUDE.md files | `/mnt/e/dev/streamvault/.claude/personas/{po,dev,test}/CLAUDE.md` |
| Workflow trigger files | `/mnt/e/dev/streamvault/.github/workflows/` |
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