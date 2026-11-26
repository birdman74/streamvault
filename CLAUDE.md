# StreamVault - Project Context

## What Is StreamVault

StreamVault is a personal streaming library tracker that allows users to manage their watched and planned movies and series. It is a portfolio project explicitly designed to demonstrate modern AI-forward engineering skills including agentic development, spec-driven development, and multi-persona agent orchestration.

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 25, Spring Boot 3.5.16 |
| Frontend | Next.js 14+, TypeScript |
| Relational DB | PostgreSQL |
| Document DB | MongoDB Atlas |
| Infrastructure | AWS EC2 (t3.micro), Docker, Docker Compose, Caddy |
| Specs | Written by the PO persona (Claude Code) in `docs/specs/` |

Deferred, not yet used by the application (see [AI and Infrastructure Tooling](docs/specs/backlog.md#ai-and-infrastructure-tooling)): Spring AI, LiteLLM (AI gateway; local container only), Ollama (local inference), AWS Bedrock (cloud inference), Tailscale (private networking). Do not add code that depends on them.

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
|    |- specs/          # Epics, stories, backlog (PO); design/ and status/ per story
|    |- adr/            # Architecture Decision Records
|- .github/
|    |- workflows/      # Pipeline adapters, reusable persona-run, CI, sync jobs
|    |- pipeline-config.env  # Every pipeline knob (ADR-005)
|- scripts/            # Pipeline rules (transition.sh), queue, checks
|- .claude/
|    |- personas/
|         |- po/CLAUDE.md
|         |- dev/CLAUDE.md
|         |- test/CLAUDE.md
|- docker-compose.yml
|- .env.example
|- CLAUDE.md            # this file
|- CONTRIBUTING.md      # Conventions, ADR index
|- STATUS.md            # Project dashboard
```

## Coding Conventions

- Language: Java is the primary backend language
- Iteration variables in order: i, j, k, l
- No em-dashes in any output or documentation
- Credentials only in gitignored .env files or GitHub Actions secrets, never hardcoded in committed files
- Use ${VAR} references in all committed config files
- Line endings: LF (enforced via .gitattributes)
- Branch: main
- Commit style: conventional commits (chore:, feat:, fix:, docs:, test:)

## Agent Workflow

This project uses a multi-persona, test-first agentic workflow. Your own persona file (`~/.claude/CLAUDE.md`) holds your detailed instructions; this is the overview.

1. PO defines epics and user stories with acceptance criteria; Brian reviews and merges the specs
2. Test goes first: test plan, API contracts and failing tests for the story
3. Dev reviews Test's design; they iterate until Dev agrees
4. Dev implements until every test passes and opens a PR
5. Test verifies and posts a verdict; Dev fixes until Test approves
6. Brian reviews and merges

Personas settle disagreements directly (design feedback, defective tests, and acceptance-criteria questions to PO), each within a round limit in `.github/pipeline-config.env`. Brian is pulled in through a `needs-brian` issue only when a limit is exceeded or a persona stalls (ADR-005).

Each persona operates in its own Docker container with a scoped Git identity. No persona merges to main directly; all work goes through Brian's review.

## Git Identity Per Persona

Commits will show the originating persona in git log:
- PO commits: `claude-streamvault-po`
- Dev commits: `claude-streamvault-dev`
- Test commits: `claude-streamvault-test`
- Human commits: Brian Campbell

## Cost Constraints

AWS free tier has expired. Every infrastructure decision must weigh cost. The EC2 instance is stopped when not in active use. MongoDB Atlas M0 free tier is used for document storage. Prefer free/local tooling during development (Ollama) and route to Bedrock only for production demo features.