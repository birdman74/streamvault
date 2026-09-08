# Contributing to StreamVault

This document defines the standards and conventions for all work on StreamVault -- whether authored by human or AI persona. All contributors (Brian, PO, Dev, Test) are expected to follow these standards. When in doubt, check `docs/adr/` for architectural decisions before inventing your own pattern.

---

## Workflow

### Branch Naming
- Feature branches: `feature/story-NNN-short-kebab-case-description`
- Spec branches: `specs/epic-NNN-short-description`
- No direct commits to `main` -- ever

### Commit Messages
Follow [Conventional Commits](https://www.conventionalcommits.org/):

| Prefix | Use |
|---|---|
| `feat(story-NNN):` | New feature implementation |
| `fix(story-NNN):` | Bug fix |
| `test(story-NNN):` | Test additions or changes |
| `docs:` | Documentation only |
| `chore:` | Tooling, config, dependencies |
| `refactor:` | Code change with no behavior change |

### Pull Requests
- Bot account (`briankcampbell-streamvault-bot`) opens all feature PRs
- Brian (`birdman74`) is CODEOWNER -- his approval is required before any merge
- CI must pass before merge
- Test persona must post a test run summary as a PR comment before Brian reviews

### STATUS.md
Every meaningful commit must include a STATUS.md update in the same commit:
- Update `Last Updated` date to today (YYYY-MM-DD)
- Update story status in the Epics & Stories section
- Note any blockers in Blocked Items

---

## Java / Spring Boot Standards

### Language and Framework
- Java 25 (Temurin)
- Spring Boot 3.5.x
- Spring AI for all AI-related integration

### Dependency Injection
- Constructor injection only -- never field injection (`@Autowired` on fields)
- All dependencies declared `final` in constructor-injected classes

### Exception Handling
- No swallowed exceptions -- every `catch` block must log or rethrow
- Domain exceptions extend a common base or are purpose-built (e.g. `InvalidRatingTypeException`)
- `GlobalExceptionHandler` handles all API error responses -- do not return error responses directly from controllers

### Database
- All PostgreSQL schema changes via Flyway migrations
- Migration files named: `V{N}__{description}.sql`
- Never modify an existing migration -- always add a new one
- MongoDB used only for flexible/unstructured media metadata -- never for relational data

### API Design
- All endpoints require authentication unless explicitly listed in `SecurityConfig.permitAll()`
- User identity always resolved from JWT via `@AuthenticationPrincipal` -- never from request body
- Input validation on all request DTOs using Bean Validation (`@NotBlank`, `@NotNull`, etc.)
- HTTP status codes: 200 OK, 201 Created, 400 Bad Request (validation), 401 Unauthorized, 403 Forbidden, 404 Not Found

### Iteration Variables
Use in this order: `i`, `j`, `k`, `l`

### Credentials
- Never hardcode credentials in committed files
- All secrets via `.env` (gitignored) -- see `.env.example` for required variables

---

## Testing Standards

### Test Naming
```
should_[expected behavior]_when_[condition]
```
Examples:
- `should_return200WithCurrentRatingType_when_authenticatedUserRequestsSettings`
- `should_throwInvalidRatingTypeException_when_ratingTypeValueIsUnsupported`

### Two-Layer Testing Convention (see ADR-001)

**Layer 1: Controller Slice Tests (`@WebMvcTest`)**
- Use `@WithMockUser` or a custom `@WithSecurityContext` for authenticated principal
- Keep `addFilters = false` consistent with existing tests
- Always `@Import(SecurityConfig.class)` when the controller uses `@AuthenticationPrincipal`
- Do NOT set `SecurityContextHolder` directly in test code

**Layer 2: Security Integration Tests (`@SpringBootTest`)**
- Use real JWT round-trips: register -> login -> extract token -> authenticate request
- Exercise the full security filter chain end-to-end
- See `SecurityConfigAuthFlowTest` as the reference implementation

### General Test Rules
- All tests must be deterministic -- no random data, no time-dependent assertions without mocking
- Tests must clean up after themselves -- no state pollution between test methods
- Every acceptance criterion (AC-N) must have at least one test
- Regression tests required for any change to shared infrastructure (security config, schema, shared services)

### Acceptance Criteria Coverage
Test persona maps every test to its AC-N label in the test run summary. Dev adds unit tests for lower-level concerns not covered by Test's integration tests.

---

## Architecture Decision Records

Before making any non-obvious architectural or testing decision, consult:

```
docs/adr/
```

Existing ADRs:
- [ADR-001](docs/adr/ADR-001-spring-mvc-test-auth-pattern.md) -- Spring MVC controller test authentication pattern

If your decision is not covered by an existing ADR, flag it to Brian. New ADRs are created when a decision has significant long-term consequences and alternatives were considered.

---

## Line Endings

All files use LF line endings, enforced by `.gitattributes`. When pasting content into files on Windows, verify the line ending setting in VS Code (bottom right corner) shows `LF` before saving.

---

## What Belongs Where

| Content type | Location |
|---|---|
| Architectural decisions with alternatives | `docs/adr/` |
| Coding and testing conventions | `CONTRIBUTING.md` (this file) |
| Story requirements and acceptance criteria | `docs/specs/story-NNN-*.md` |
| Design artifacts (test plans, contracts) | `docs/specs/design/` |
| Deferred work and open questions | `docs/specs/backlog.md` |
| Project health and story status | `STATUS.md` |
| Project history and lessons learned | `ai-dev-toolkit/DEVLOG.md` |