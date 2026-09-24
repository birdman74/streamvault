# StreamVault

A personal streaming library tracker built as a portfolio project demonstrating agentic software development, spec-driven TDD, and multi-persona AI orchestration.

---

## What this project is

StreamVault is a full-stack web application for tracking your personal streaming library -- rating films and series, tracking watch status, and organizing content across platforms. It is also, deliberately, a live demonstration of an engineering workflow where three AI personas (Product Owner, Developer, and Test Engineer) collaborate autonomously to build production-quality software from spec through merged PR.

The application is real and functional. The workflow building it is the point.

---

## Tech Stack

**Backend:** Java 25, Spring Boot 3.5.x, Spring Security, JWT authentication, Google OAuth2, Flyway migrations
**Frontend:** Next.js, TypeScript
**Databases:** PostgreSQL (relational data), MongoDB Atlas (flexible media metadata)
**Infrastructure:** AWS EC2 t3.micro, Docker Compose, Caddy reverse proxy
**CI/CD:** GitHub Actions (self-hosted runner), JaCoCo (75% coverage threshold), Codecov, CodeQL

---

## The Agentic Workflow

Three Claude Code personas operate as scoped Docker containers with distinct Git identities. They coordinate entirely through GitHub -- branches, commits, pull requests, and issue labels -- without direct communication.

For the full annotated workflow diagram including all GitHub Actions triggers, feedback loops, and escalation paths, see the [Agentic Workflow Diagram](https://github.com/birdman74/ai-dev-toolkit/blob/main/docs/agentic-workflow-diagram.md) in ai-dev-toolkit.

Each step is triggered automatically by a GitHub Actions workflow. The self-hosted runner launches the appropriate persona container with a prompt. Brian's roles are spec approval, final PR review, and resolving design conflicts between Dev and Test when they cannot reach agreement after three rounds.

### Personas

| Persona | Image | Responsibility |
|---|---|---|
| PO (`claude-po-img`) | Claude Code + gh CLI | Writes epics and user stories, creates GitHub Issues, opens spec PRs |
| Dev (`claude-dev-img`) | Claude Code + Java 25 + Maven + gh CLI | Reviews Test's design, implements, opens feature PRs |
| Test (`claude-dev-img`) | Claude Code + Java 25 + Maven + gh CLI | Writes test plans and API contracts, writes failing tests first, does final verification |

### Queue Management

Stories are tracked as GitHub Issues labeled `story`. The queue manager (`scripts/next-story.sh`) reads prerequisites from spec files, checks which story issues are closed (completed), and returns the next eligible story in dependency order. Only one story is in progress at a time.

---

## Project Standards

- **Spec-driven:** Every story has a markdown spec in `docs/specs/` with acceptance criteria before any code is written
- **TDD-first:** Test writes failing tests before Dev writes a single line of implementation
- **ADRs:** Architecture decisions recorded in `docs/adr/` so future stories follow established patterns
- **CONTRIBUTING.md:** Coding conventions, testing standards, and PR process documented for all contributors (human and AI)
- **Coverage:** 75% instruction coverage enforced by JaCoCo on every PR; Codecov tracks trends over time
- **Security scanning:** CodeQL runs on every push to main and every PR

---

## Stories Completed

| Story | Description |
|---|---|
| STORY-001 | Email/password registration and login |
| STORY-002 | Google OAuth2 sign-in |
| STORY-003 | Local build and test tooling in Dev and Test containers |
| STORY-004 | GitHub PR automation for Dev and Test personas |
| STORY-005 | Account settings for rating type preference |

Active work: Personal Streaming Library epic (STORY-006 through STORY-019)

---

## Repository Structure

```
streamvault/
├── backend/                    Spring Boot application
│   └── src/
│       ├── main/java/          Production code
│       └── test/java/          Test code (written by Test persona first)
├── frontend/                   Next.js application (in progress)
├── docs/
│   ├── specs/                  Story specs and design artifacts
│   │   └── design/             Test plans, API contracts, agreed designs
│   └── adr/                    Architecture Decision Records
├── .claude/personas/           CLAUDE.md files for each persona
├── .github/workflows/          GitHub Actions trigger chain
├── scripts/                    Queue manager and utility scripts
├── infrastructure/             Caddyfile and deployment config
├── CONTRIBUTING.md             Coding and testing standards
└── STATUS.md                   Current project health and story status
```

---

## Related

**[ai-dev-toolkit](https://github.com/birdman74/ai-dev-toolkit)** -- The reusable framework extracted from this project: Dockerfile templates, GitHub Actions workflow templates, persona CLAUDE.md templates, and documentation for running this workflow on a new project.

---

## Status

Active development. See [STATUS.md](STATUS.md) for current story progress and [the project board](https://github.com/users/birdman74/projects/2) for the full story queue.