package com.streamvault.backend.testsupport;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import com.streamvault.backend.auth.AuthenticatedUser;

/**
 * Builds the {@link SecurityContext} for {@link WithMockAuthenticatedUser}. Creates a fresh
 * {@link SecurityContextImpl} so it never depends on {@code SecurityContextHolder} state at
 * construction time, and installs a {@link UsernamePasswordAuthenticationToken} whose principal is
 * an {@link AuthenticatedUser} matching the annotation attributes.
 */
public class WithMockAuthenticatedUserSecurityContextFactory
        implements WithSecurityContextFactory<WithMockAuthenticatedUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockAuthenticatedUser annotation) {
        AuthenticatedUser principal = new AuthenticatedUser(annotation.userId(), annotation.email());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, List.of());

        SecurityContext context = new SecurityContextImpl();
        context.setAuthentication(authentication);
        return context;
    }
}
