# STORY-003: Local Build & Test Tooling in Dev and Test Containers

## As a...
Dev or Test persona operating inside my Docker container

## I want to...
run the project's build and unit-test commands directly inside my container

## So that...
I can validate an implementation against a story's acceptance criteria before committing, without Brian running the build on my behalf

## Acceptance Criteria
- [ ] The Dev persona container image includes Java 21 and Maven (or the Maven wrapper) such that `mvn clean verify` runs successfully against `backend/` with no host intervention
- [ ] The Dev persona container image includes Node.js and npm such that the frontend build and test commands run successfully against `frontend/` with no host intervention
- [ ] The Test persona container image includes the same Java/Maven and Node/npm tooling as the Dev container, so it can independently run unit tests and Spring MockMvc tests against the implementation
- [ ] Running these build/test commands inside either container does not require mounting the host Docker socket or any Docker-in-Docker setup
- [ ] `mvn clean verify` runs successfully as a smoke test against the current backend skeleton (from the `chore(STORY-001)` bootstrap commit) inside both containers, with output visible in the persona's session

## Notes
- Executed by Brian (not Dev persona). This is a container image/build change to the Dev and Test containers themselves — Dev cannot upgrade its own container from inside that container. Brian is the assignee for this story.
- This story covers unit-level and Spring MockMvc-level testing only. Testcontainers-based integration testing is explicitly deferred, per Brian's decision, until there's a clearer security posture around mounting the host Docker socket into a container. Tracked in `docs/specs/backlog.md`.
- Tackled first — STORY-004 depends on this story's tooling being in place.

## Out of Scope
- Testcontainers / any integration test requiring a Docker daemon inside the container (see `docs/specs/backlog.md`)
- Any change to what Dev or Test are authorized to commit, push, or merge

## Prerequisites: - None
