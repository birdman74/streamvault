# STORY-006 Log: TMDB Search and Browse

> Full phase-by-phase trace of Dev/Test work on this story. STATUS.md carries only a
> one-line current status for this story; this file is the detailed record used during
> PR review. Newest entries at the top.

Spec: `docs/specs/story-006-tmdb-search-browse.md`
Branch: `feature/story-006-tmdb-search-browse`
PR: #26

---

## Phase 3: Test final PR verification

Complete on PR #26: **APPROVED**. Pulled the branch and ran `mvn clean verify` - full suite green
147/147, JaCoCo 75% instruction gate passes. Every AC-1..AC-8 is covered by at least one passing
test and every cross-story invariant holds.

Regression analysis of the diff: the only shared-runtime change is one additive
`@ExceptionHandler(TmdbUnavailableException.class)` in `GlobalExceptionHandler` (no existing mapping
altered; the other branches stay covered by `AccountSettingsControllerTest` and
`AuthControllerGoogleTest`, both green); `application.yml` gains a `tmdb.*` block whose `api-key` has
no default, matching the existing `GOOGLE_CLIENT_ID` / `JWT_SECRET` precedent, and the context-load
smoke test (`StreamvaultBackendApplicationTests`) plus the two other full-context `@SpringBootTest`
classes pass with the `tmdb.api-key` `@DynamicPropertySource` entry Dev added; no DB schema,
`SecurityConfig`, repository, or shared service was touched (`TmdbPackageReadOnlyConventionTest` pins
the migration set at V1..V4 and the `TmdbEndpointsSecurityTest` boundary tests confirm `/api/health`
and `/api/auth/me` are unmoved). No new regression tests were required - every shared-infrastructure
change is additive and already covered by an existing passing test.

**Verdict: Test APPROVED on PR #26. Awaiting Brian's review and merge.**

---

## Phase 2: Dev implementation

New `com.streamvault.backend.tmdb` package: `TmdbController` (`GET /api/tmdb/search`,
`GET /api/tmdb/browse`), `TmdbCatalogService` (owns `page` null -> 1 and `list` null -> `POPULAR`
defaults, no persistence collaborator), `TmdbGateway` + `RestClientTmdbGateway` (v3 `api_key` query
param, `/search/multi` and `/trending/all/{week,day}`, `movie`/`tv` -> `MOVIE`/`SERIES` with `person`
dropped, leading-4-digit year parse, absolute `posterUrl`, every `RestClientException`/non-2xx
wrapped in `TmdbUnavailableException`), the two wire enums, the `TmdbResult`/`TmdbResultPage`
projection, `TmdbSearchRequest`/`TmdbBrowseRequest` query-bound validated records, and
`TmdbUnavailableException`. `GlobalExceptionHandler` gains one additive
`@ExceptionHandler(TmdbUnavailableException.class)` -> 502 with the fixed message (cause logged,
never in body). No `SecurityConfig` change (routes fall under `anyRequest().authenticated()`); no
entity, repository, or Flyway migration (set stays V1..V4). Config: `application.yml` gains
`tmdb.api-key`/`base-url`/`image-base-url`; `.env.example` gains `TMDB_IMAGE_BASE_URL`. The three
`tmdb.*` props follow the agreed contract's no-default form for `api-key` (consistent with
`GOOGLE_CLIENT_ID`), so the pre-existing full-context `@SpringBootTest` classes
(`StreamvaultBackendApplicationTests`, `SecurityConfigAuthFlowTest`, `UserTableConstraintsTest`) each
add `tmdb.api-key` to their existing `@DynamicPropertySource` block, mirroring how they already
supply `app.jwt.secret` / `app.google.client-id`. Dev added lower-level unit tests below Test's
integration boundary: `RestClientTmdbGatewayEdgeCasesTest` (year-parse edge cases, payload ordering
after the person drop, absent/null `results` -> `List.of()`), `TmdbWireEnumTest` (enum `name()`
round-trip), `TmdbUnavailableExceptionTest` (cause retention). Full suite green: 147/147 via
`mvn clean verify` (JaCoCo 75% gate passes). All six Test-authored failing classes now pass. No
deviation from the agreed design's non-obvious framework point: the implicit `@ModelAttribute`
constructor binding routes `@NotBlank`/`@Min`/`@Max` and the `list` type-mismatch through the
existing `MethodArgumentNotValidException` handler exactly as predicted; no new handler branch was
needed.

---

## Phase 1: Test goes first

Complete on branch `feature/story-006-tmdb-search-browse`. Test plan
(`docs/specs/design/story-006-test-plan.md`) maps every AC-1..AC-8 plus cross-story invariants to
named tests; API contracts (`docs/specs/design/story-006-api-contracts.md`) define
`GET /api/tmdb/search` and `GET /api/tmdb/browse`, a shared `TmdbResultPage` / `TmdbResult`
projection, `MOVIE`/`SERIES` and `POPULAR`/`TRENDING` wire enums, 400 validation envelopes, 401
reuse of the existing entry point, and a new 502 `TmdbUnavailableException` mapping. Six failing
test classes committed: `TmdbControllerTest`, `TmdbCatalogServiceTest`, `RestClientTmdbGatewayTest`
(MockRest, mirrors `GoogleTokenInfoVerifierTest`), `dto/TmdbSearchRequestValidationTest`,
`TmdbEndpointsSecurityTest` (Layer 2 real filter chain, AC-8 + `SecurityConfig` invariant), and
`TmdbPackageReadOnlyConventionTest` (source scan guarding AC-7 read-only + no new migration). RED
confirmed: `mvn clean test-compile` fails only on the nine unimplemented
`com.streamvault.backend.tmdb` symbols, consistent with the story-002/story-005 RED convention. This
story adds no Flyway migration and no `SecurityConfig` change.

Phase 1 design review complete: Dev agreed on round 1 (`docs/specs/design/story-006-agreed.md`), no
blocking concerns against AC-1..AC-8 or the cross-story invariants. One non-obvious framework point
recorded for Brian: `TmdbSearchRequest` / `TmdbBrowseRequest` are the first query-string-bound
validated request objects in this codebase (implicit `@ModelAttribute` constructor binding,
Spring 6.1+), and validation failures are expected to route through the existing
`MethodArgumentNotValidException` handler; Dev will flag to Brian rather than adding an undocumented
handler branch if implementation shows otherwise.