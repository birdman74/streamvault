# Epic: Autonomous Agentic Workflow

## Goal
Enable the Dev and Test personas to operate autonomously inside their Docker containers: compiling and running their own build/test tooling, and interacting with GitHub directly to open pull requests and post verification results as comments. Today Brian has to manually run builds and create PRs on the personas' behalf, which breaks the multi-persona agentic workflow this project exists to demonstrate. This epic closes that gap while keeping the existing hard rule intact: no persona ever merges to main, that stays Brian's action alone.

## Stories
- STORY-003: Local Build & Test Tooling in Dev and Test Containers (assignee: Brian, executed first)
- STORY-004: GitHub PR Automation for Dev and Test Personas (assignee: Brian, depends on STORY-003)

Both stories are infrastructure changes to the persona containers themselves. Neither is implemented by the Dev persona — a container cannot upgrade itself from inside itself. Brian executes both directly.

## Out of Scope (see docs/specs/backlog.md)
- Testcontainers / Docker-in-Docker support for integration tests (deferred until Docker socket security posture is clarified)
- GitHub App-based authentication (a scoped personal access token was chosen instead, for simplicity)
- Any automation that grants merge-to-main capability to a persona
