package com.b2ta.api.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Authentication token holding a {@link TaPrincipal}.
 *
 * <p>Constructed already authenticated: it is only ever created after
 * {@link CognitoJwtAuthenticationFilter} has verified the token signature and claims.
 */
public class TaAuthentication extends AbstractAuthenticationToken {

    private final TaPrincipal principal;

    public TaAuthentication(TaPrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_TA")));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public TaPrincipal getPrincipal() {
        return principal;
    }

    /**
     * Always {@code null}.
     *
     * <p>The access token is not retained past validation so it cannot end up in a log record or
     * an error response (Requirement 18.11).
     */
    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public String getName() {
        return principal.taId().toString();
    }
}
