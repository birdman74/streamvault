# StreamVault — Project Context

## What Is StreamVault

StreamVault is a personal streaming library tracker that allows users to manage their watched and planned movies and series. It is a portfolio project explicitly designed to demonstrate modern AI-forward engineering skills including agentic development, spec-driven development, and multi-persona agent orchestration.

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.x, Spring AI |
| Frontend | Next.js 14+, TypeScript |
| Relational DB | PostgreSQL |
| Document DB | MongoDB Atlas |
| AI Gateway | LiteLLM |
| Local Inference | Ollama |
| Cloud Inference | AWS Bedrock |
| Infrastructure | AWS EC2 (t3.micro), Docker, Docker Compose, Caddy |
| Networking | Tailscale |
| Spec Tooling | OpenSpec, OpenCode |

## Repository Structure

```
streamvault/
|- backend/src/
|- frontend/
|- infrastructure/
|    |- docker/
|    |- ec2/
|    |- litellm-config.yml
|- docs/
|    |- specs/          # OpenSpec output lives here
|    |- adr/            # Architecture Decision Records
|- .claude/
|    |- personas/
|         |- po/CLAUDE.md
|         |- dev/CLAUDE.md
|         |- test/CLAUDE.md
|- docker-compose.yml
|- .env.example
|- CLAUDE.md            # this file
```

## Coding Conventions

- Language: Java is the primary backend language
- Iteration variables in order: i, j, k, l
- No em-dashes in any output or documentation
- All credentials via .env file only — never hardcoded in committed files
- Use ${VAR} references in all committed config files
- Line endings: LF (enforced via .gitattributes)
- Branch: main
- Commit style: conventional commits (chore:, feat:, fix:, docs:, test:)

## Agent Workflow

This project uses a multi-persona agentic workflow:

1. PO persona defines epics and user stories with acceptance criteria
2. Dev persona implements against those stories
3. Test persona writes and runs tests against the implementation
4. Brian (human) reviews and approves before anything merges to main

Each persona operates in its own Docker container with a scoped Git identity. No persona merges to main directly — all work goes through Brian's review.

## Git Identity Per Persona

Commits will show the originating persona in git log:
- PO commits: `claude-streamvault-po`
- Dev commits: `claude-streamvault-dev`
- Test commits: `claude-streamvault-test`
- Human commits: Brian Campbell

## Cost Constraints

AWS free tier has expired. Every infrastructure decision must weigh cost. The EC2 instance is stopped when not in active use. MongoDB Atlas M0 free tier is used for document storage. Prefer free/local tooling during development (Ollama) and route to Bedrock only for production demo features.