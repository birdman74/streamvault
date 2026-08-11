# StreamVault — Project Status

> This file is the source of truth for project health and progress.
> Updated by Brian (infrastructure/review), PO persona (epics/stories), Dev persona (implementation), and Test persona (verification).
> **Updated as part of every meaningful commit — do not let this file fall behind.**

---

## Health Indicator

### Rules
To compute current health, use `Last Updated` date and `Blocked Items` section below:

| Status | Condition |
|---|---|
| 🟢 Green | Last Updated within 3 days AND no blocked items |
| 🟡 Yellow | Last Updated 4-7 days ago OR any blocked items with a plan to unblock |
| 🔴 Red | Last Updated 7+ days ago OR blocked with no plan to unblock |

### Last Updated
2026-08-11

### Current Phase
Application Development — User Authentication epic drafted by PO, pending Brian's review before Dev picks it up.

---

## Infrastructure Tasks

### EC2 / Docker
- [x] EC2 instance created (`streamvault-server`, t3.micro, Ubuntu 24.04, 20GB gp3)
- [x] Elastic IP assigned (`54.166.127.211`)
- [x] Security group configured (SSH/My IP, HTTP/HTTPS public)
- [x] Docker installed on EC2 (v29.6.2)
- [x] Docker Compose installed on EC2 (v5.3.1)
- [x] AWS Budget alarm configured
- [x] Swap file added to EC2 (2GB, persistent via /etc/fstab)
- [x] `restart: unless-stopped` added to all compose services
- [x] PostgreSQL container stable on EC2
- [x] LiteLLM deferred from EC2 (t3.micro memory constraint — revisit when upgrading instance for production demo)
- [x] Caddy reverse proxy added to compose and responding on port 80/443
- [x] Split docker-compose.yml (local dev) and docker-compose.prod.yml (EC2)
- [ ] Domain name pointed at Elastic IP

### Local Dev Environment
- [x] Ollama deferred — AMD RX 7600 XT lacks DirectML support in Ollama Docker image on WSL2/Windows; CPU-only inference not performant enough to justify inclusion. Will revisit if Claude Pro API costs become a concern during development.
- [x] Local LiteLLM deferred alongside Ollama — will revisit when Ollama is unblocked or an alternative local inference path is identified.
- [x] MongoDB Atlas M0 free tier created
- [x] MongoDB Atlas connection string added to .env (local + EC2)

### CI/CD
- [x] GitHub Actions workflow: build on push to main

### Agentic Workflow Infrastructure
- [x] Claude Code Docker image built (`claude-experience-img`)
- [x] PO persona container configured (`streamvault-po.sh`)
- [x] Dev persona container configured (`streamvault-dev.sh`)
- [x] Test persona container configured (`streamvault-test.sh`)
- [x] Shared GitHub deploy key generated and registered
- [x] Per-persona Git identity configured
- [x] CLAUDE.md files in place (project root + all 3 personas)

---

## Application Milestones

- [x] First epic defined by PO (user authentication)
- [ ] First story implemented by Dev
- [ ] First story verified by Test
- [ ] First AI-powered feature end-to-end (LiteLLM + Ollama)
- [ ] Demoable to an interviewer
- [ ] AWS Bedrock production path confirmed

---

## Epics & Stories

### Epic: User Authentication
Spec: `docs/specs/epic-user-authentication.md` — READY FOR DEV (all open questions resolved by Brian 2026-08-11)

- [ ] STORY-001: Email/Password Registration and Login (`docs/specs/story-001-email-password-auth.md`)
- [ ] STORY-002: Google OAuth2 Sign-In (`docs/specs/story-002-google-oauth.md`)

Deferred work parked in `docs/specs/backlog.md`: Account Settings, Password Reset & Email Verification, server-side JWT revocation, Google/email account linking.

### Epic: Autonomous Agentic Workflow
Spec: `docs/specs/epic-autonomous-agentic-workflow.md` — READY (assignee: Brian, both stories are infrastructure changes to the persona containers themselves, not Dev persona work)

- [x] STORY-003: Local Build & Test Tooling in Dev and Test Containers (`docs/specs/story-003-dev-test-build-tooling.md`) — tackled first
- [ ] STORY-004: GitHub PR Automation for Dev and Test Personas (`docs/specs/story-004-github-pr-automation.md`) — depends on STORY-003

Deferred work parked in `docs/specs/backlog.md`: Testcontainers/Docker-in-Docker for Test persona, GitHub App-based auth.

---

## Blocked Items

_None. STORY-001 and STORY-002 are unblocked and ready for Dev to pick up._

---

## Stack Reference

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.x, Spring AI |
| Frontend | Next.js 14+, TypeScript |
| Relational DB | PostgreSQL |
| Document DB | MongoDB Atlas (M0 free tier) |
| AI Gateway | LiteLLM |
| Local Inference | Ollama |
| Cloud Inference | AWS Bedrock |
| Infrastructure | AWS EC2 t3.micro, Docker, Docker Compose, Caddy |
| Networking | Tailscale |
| Spec Tooling | OpenSpec, OpenCode |

---

## Key References

| Resource | Detail |
|---|---|
| GitHub Repo | https://github.com/birdman74/streamvault |
| EC2 Elastic IP | 54.166.127.211 |
| SSH Key | C:\Users\brian\.ssh\streamvault-key.pem |
| EC2 User | ubuntu |
| License | All Rights Reserved |

---

## Architecture Decisions

| Decision | Rationale |
|---|---|
| Dual-store (PostgreSQL + MongoDB) | PostgreSQL for structured relational data (users, watch history); MongoDB for flexible media metadata where schema varies significantly (home movies vs TMDB entries) |
| LiteLLM as AI gateway | Provider-agnostic routing so application code never changes when switching between Ollama (free local dev) and AWS Bedrock (production demos) |
| LiteLLM deferred from EC2 | t3.micro has 1GB RAM; LiteLLM consumed ~500MB leaving insufficient headroom for Spring Boot. Will revisit on instance upgrade. |
| Spring AI over direct SDK | First-class Java abstraction for AI that enterprise Java shops are adopting; demonstrates modern Java AI integration patterns |
| Single Claude Code image, three personas | Tooling needs are identical across personas; behavior is driven entirely by CLAUDE.md system prompts |
| All Rights Reserved license | Portfolio repo must be publicly visible for recruiters while protecting original work |
| Ollama deferred (local) | AMD RX 7600 XT GPU passthrough to Docker on WSL2/Windows uses DirectML which Ollama does not support. CPU-only inference is too slow for practical use. LiteLLM will route to Claude API during development and AWS Bedrock for production demos. Will revisit if Claude Pro quota or API costs become a concern. |