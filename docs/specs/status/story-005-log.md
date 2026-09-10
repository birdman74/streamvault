# STORY-005 Log: Account Settings for Rating Type Preference

> Full phase-by-phase trace of Dev/Test work on this story. STATUS.md carries only a
> one-line current status for this story; this file is the detailed record used during
> PR review. Newest entries at the top.

Spec: `docs/specs/story-005-account-settings-rating-type.md`
PR: #25

---

## Phase 4: Brian-review fix re-verification (Test)

Test re-verification of the Phase 4 Dev fix on PR #25 (commit 2af535c) complete. Dev's fix is
test-infrastructure only: added `testsupport/WithMockAuthenticatedUser` + its
`WithMockAuthenticatedUserSecurityContextFactory` per `docs/specs/design/story-005-brian-review-r1.md`,
refactored `AccountSettingsControllerTest` off the `@BeforeEach`/`@AfterEach` `SecurityContextHolder`
seeding onto `@WithMockAuthenticatedUser(userId = 42L, email = "user@example.com")`, and reworded one
Javadoc line so the source-scanning guard no longer matches prose. No production code changed by this
fix (diff: STATUS.md + 2 new test-support classes + 2 test files). Full suite green: 77/77 via
`mvn clean verify`. Convention now enforced by `ControllerSliceTestAuthConventionTest` (both halves)
and pinned by `AccountSettingsControllerPrincipalConventionTest`. All AC-1..AC-6 and all cross-story
invariants covered by passing tests.

**Verdict: Test APPROVED on PR #25. Awaiting Brian's review and merge.**