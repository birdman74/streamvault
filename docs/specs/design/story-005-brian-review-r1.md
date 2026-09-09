# story-005 — Brian Review Round 1 (Changes Requested on PR #25)

## Gap Brian identified

> `AccountSettingsControllerTest` uses `SecurityContextHolder.setContext()` directly to inject
> the authenticated principal. Per ADR-001 (`docs/adr/ADR-001-spring-mvc-test-auth-pattern.md`)
> and `CONTRIBUTING.md`, the agreed convention for `@WebMvcTest` slices is to use `@WithMockUser`
> or a custom `@WithSecurityContext` annotation instead. Please refactor
> `AccountSettingsControllerTest` to follow this convention. This sets the pattern for all future
> controller slice tests in the codebase.

## Why `@WithMockUser` alone is not enough here

`AccountSettingsController` resolves `@AuthenticationPrincipal AuthenticatedUser principal` and
calls `principal.userId()`. `@WithMockUser` seeds a Spring Security `UserDetails` principal, not
an `AuthenticatedUser`, so `@AuthenticationPrincipal AuthenticatedUser` would resolve to `null`
and the controller would NPE. ADR-001 anticipates this: *"or a custom `@WithSecurityContext` for
more complex principal shapes"*. The codebase therefore needs one reusable custom annotation.

## Failing tests added in this commit (Phase 4)

| Test | Covers | Fails now because |
|---|---|---|
| `convention/ControllerSliceTestAuthConventionTest.should_notReferenceSecurityContextHolderDirectly_when_testIsAControllerSliceTest` | Negative half of the convention: no `@WebMvcTest` slice test touches `SecurityContextHolder` | `AccountSettingsControllerTest` seeds the principal via `SecurityContextHolder.getContext().setAuthentication(...)` in `@BeforeEach` |
| `convention/ControllerSliceTestAuthConventionTest.should_populatePrincipalWithAWithAnnotation_when_sliceTestNeedsAnAuthenticatedPrincipal` | Positive half: slice tests that exercise `@AuthenticationPrincipal` declare it with a `@With...` annotation | `AccountSettingsControllerTest` uses neither `@WithMockUser` nor a `@WithSecurityContext` annotation |
| `settings/AccountSettingsControllerPrincipalConventionTest` (both methods) | Reference implementation of the convention; `@AuthenticationPrincipal AuthenticatedUser` still resolves and its `userId()` is threaded into the service call | `com.streamvault.backend.testsupport.WithMockAuthenticatedUser` does not exist yet — test sources fail to compile until Dev adds it |

## Contract for the custom annotation Dev must add

Create the annotation and its factory under `backend/src/test/java/com/streamvault/backend/testsupport/`:

```java
package com.streamvault.backend.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.test.context.support.WithSecurityContext;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@WithSecurityContext(factory = WithMockAuthenticatedUserSecurityContextFactory.class)
public @interface WithMockAuthenticatedUser {

    long userId() default 1L;

    String email() default "user@example.com";
}
```

Factory requirements:

- Implements `WithSecurityContextFactory<WithMockAuthenticatedUser>`.
- Returns a `SecurityContext` whose `Authentication` is a
  `UsernamePasswordAuthenticationToken` with:
  - principal = `new AuthenticatedUser(annotation.userId(), annotation.email())`
  - credentials = `null`
  - authorities = empty list
- No dependence on `SecurityContextHolder` state at construction time (create a fresh
  `SecurityContextImpl`).

This is the annotation the whole codebase uses going forward for any slice test needing an
`AuthenticatedUser` principal.

## Dev's fix (triggered by this push)

1. Add `WithMockAuthenticatedUser` + `WithMockAuthenticatedUserSecurityContextFactory` per the
   contract above.
2. Refactor `AccountSettingsControllerTest`:
   - delete the `@BeforeEach` / `@AfterEach` `SecurityContextHolder` setup and the
     `UsernamePasswordAuthenticationToken` / `SecurityContextHolder` imports
   - annotate the class (or each authenticated test method) with
     `@WithMockAuthenticatedUser(userId = 42L, email = "user@example.com")`
   - keep `@AutoConfigureMockMvc(addFilters = false)` and `@Import(SecurityConfig.class)`
3. Leave `SecurityConfigAuthFlowTest` (Layer 2) untouched — it is already the agreed reference
   for the real-filter-chain layer.

## After the fix

- `ControllerSliceTestAuthConventionTest` — both methods pass (no slice test references
  `SecurityContextHolder`; the one that needs a principal uses `@WithMockAuthenticatedUser`).
- `AccountSettingsControllerPrincipalConventionTest` — compiles and passes.
- `AccountSettingsControllerTest` — unchanged assertions, still green, now on the convention.
- Full suite green via `mvn clean verify`.

## Not changed

No acceptance-criteria coverage changes. AC-1 through AC-6 mappings in
`story-005-test-plan.md` are unaffected; this round is purely test-infrastructure alignment
with ADR-001.
