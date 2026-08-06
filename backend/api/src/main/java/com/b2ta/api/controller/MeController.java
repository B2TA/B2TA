package com.b2ta.api.controller;

import com.b2ta.api.security.CurrentTa;
import com.b2ta.api.security.TaPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Identity of the authenticated TA.
 *
 * <p>The SPA calls this once after sign-in to confirm the token is accepted and to learn the TA id it
 * is operating as. Reaching it at all proves authentication succeeded, so it doubles as the check the
 * client uses before rendering any session data (Requirement 18.1).
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    /** Response record; the email is included because the client displays it in the header. */
    public record Me(UUID taId, String email) {
    }

    @GetMapping
    public Me get(@CurrentTa TaPrincipal ta) {
        return new Me(ta.taId(), ta.email());
    }

    /**
     * Advertises how the deployment authenticates, so the login screen can render the right form
     * without the pool id being duplicated into the frontend build.
     */
    @GetMapping("/auth-config")
    public Map<String, Object> authConfig() {
        return Map.of("provider", "cognito");
    }
}
