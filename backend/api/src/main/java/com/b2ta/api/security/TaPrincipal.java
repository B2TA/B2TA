package com.b2ta.api.security;

import java.util.UUID;

/**
 * The authenticated TA, as resolved from a validated Cognito access token.
 *
 * @param taId       primary key of the local {@code ta_user} row; the tenant key used in every query
 * @param cognitoSub the {@code sub} claim, stable for the lifetime of the Cognito account
 * @param email      the TA's email, used only for display and never written to a log record
 */
public record TaPrincipal(UUID taId, String cognitoSub, String email) {

    @Override
    public String toString() {
        // Deliberately omits the email so an accidental log of the principal cannot leak it.
        return "TaPrincipal[taId=" + taId + "]";
    }
}
