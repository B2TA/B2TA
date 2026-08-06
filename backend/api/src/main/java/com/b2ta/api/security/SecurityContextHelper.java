package com.b2ta.api.security;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Placeholder for extracting the current TA's identity from the security context.
 * The real implementation (task 5.1) will validate Cognito JWTs and resolve the
 * TaUser from the token's 'sub' claim. For now, this returns a hard-coded UUID
 * so that the session CRUD layer can be developed and tested independently.
 */
@Component
public class SecurityContextHelper {

    // Placeholder TA ID — will be replaced by real auth context extraction in task 5.1
    private static final UUID PLACEHOLDER_TA_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * Returns the UUID of the currently authenticated TA.
     * In production, this is derived from the Cognito JWT 'sub' claim resolved
     * against the ta_user table.
     */
    public UUID getCurrentTaId() {
        return PLACEHOLDER_TA_ID;
    }
}
