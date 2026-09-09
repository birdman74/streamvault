package com.streamvault.backend.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * Custom {@code @WithSecurityContext} annotation that seeds a
 * {@link com.streamvault.backend.auth.AuthenticatedUser} principal into the security context for
 * {@code @WebMvcTest} controller slice tests, per ADR-001
 * (docs/adr/ADR-001-spring-mvc-test-auth-pattern.md).
 *
 * <p>{@code @WithMockUser} alone cannot be used for controllers that resolve
 * {@code @AuthenticationPrincipal AuthenticatedUser}, because it seeds a Spring Security
 * {@code UserDetails} principal rather than this codebase's custom principal type. This annotation
 * is the reusable convention for any slice test needing an {@code AuthenticatedUser} principal.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@WithSecurityContext(factory = WithMockAuthenticatedUserSecurityContextFactory.class)
public @interface WithMockAuthenticatedUser {

    long userId() default 1L;

    String email() default "user@example.com";
}
