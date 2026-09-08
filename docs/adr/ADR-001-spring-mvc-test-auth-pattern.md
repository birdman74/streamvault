# ADR-001: Spring MVC Controller Test Authentication Pattern

**Date**: 2026-09-07
**Status**: Accepted
**Deciders**: Brian Campbell, claude-streamvault-dev, claude-streamvault-test

---

## Context

STORY-005 introduced the first controller endpoint requiring `@AuthenticationPrincipal` resolution in a `@WebMvcTest` slice test. This surfaced a choice about how to handle authentication in controller slice tests throughout the codebase.

The existing codebase pattern uses `addFilters = false` in `@WebMvcTest` tests, which disables the real security filter chain. Without the filter chain running, standard approaches like `SecurityMockMvcRequestPostProcessors.authentication()` do not work because nothing reads the saved context back during dispatch.

Dev's implementation in STORY-005 resolved this by setting `SecurityContextHolder` directly on the test thread. This works because MockMvc dispatches synchronously and `AuthenticationPrincipalArgumentResolver` reads from the same thread-local. However this is non-standard and makes test intent less clear.

---

## Decision

Use the following two-layer testing convention throughout this codebase:

### Layer 1: Controller Slice Tests (`@WebMvcTest`)
- Use `@WithMockUser` (or a custom `@WithSecurityContext` for more complex principal shapes) to populate the security context
- Keep `addFilters = false` consistent with the existing convention
- Always `@Import(SecurityConfig.class)` when the controller uses `@AuthenticationPrincipal` -- `@WebMvcTest` does not load plain `@Configuration` classes by default
- Do NOT use `SecurityContextHolder.setContext()` directly -- this is an implementation detail of how `@WithMockUser` works internally and should not appear in test code

### Layer 2: Security Integration Tests (`@SpringBootTest`)
- Use real JWT round-trips: register -> login -> extract token -> authenticate request
- Exercise the full security filter chain end-to-end
- Validate that endpoints are actually reachable only when authenticated through the real `SecurityConfig`
- Use `SecurityConfigAuthFlowTest` (added in STORY-005) as the reference implementation

---

## Consequences

- All future controller slice tests that require an authenticated principal must use `@WithMockUser` or `@WithSecurityContext`
- `AccountSettingsControllerTest` introduced in STORY-005 should be refactored to use `@WithMockUser` to match this convention (tracked as a follow-up to STORY-005)
- Dev and Test personas must consult `docs/adr/` before making testing infrastructure decisions
- New ADRs should be created whenever a non-obvious architectural or testing decision is made that future stories should follow

---

## Alternatives Considered

**`SecurityContextHolder.setContext()` directly** -- works but non-standard, makes test intent unclear, was the initial STORY-005 implementation

**`SecurityMockMvcRequestPostProcessors.jwt()`with `addFilters = true`** -- more realistic but changes the existing `addFilters = false` convention across all controller tests, higher refactoring cost

**Move all auth tests to `@SpringBootTest`** -- too slow for routine controller unit tests, appropriate only for security integration tests (Layer 2)