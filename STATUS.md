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
2026-07-23

### Current Phase
Infrastructure Stabilization — completing local dev environment before PO/Dev/Test workflow begins.

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
- [ ] Ollama installed and running locally (WSL2)
- [ ] Local LiteLLM compose verified routing to Ollama
- [ ] MongoDB Atlas M0 free tier created
- [ ] MongoDB Atlas connection string added to .env (local + EC2)

### CI/CD
- [ ] GitHub Actions workflow: build on push to main

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

- [ ] First epic defined by PO (user authentication)
- [ ] First story implemented by Dev
- [ ] First story verified by Test
- [ ] First AI-powered feature end-to-end (LiteLLM + Ollama)
- [ ] Demoable to an interviewer
- [ ] AWS Bedrock production path confirmed

---

## Epics & Stories

_None yet. Infrastructure stabilization must complete before PO workflow begins._

---

## Blocked Items

_None. Next actions: Ollama local setup, MongoDB Atlas, then hand off to PO._

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
| GitHub Repo | https://github.com/briankcampbell/streamvault |
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