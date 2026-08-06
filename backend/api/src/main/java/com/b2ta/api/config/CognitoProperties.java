package com.b2ta.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cognito user pool coordinates, bound from {@code aws.cognito.*}. */
@Data
@ConfigurationProperties(prefix = "aws.cognito")
public class CognitoProperties {

    /** User pool id, for example {@code us-east-1_AbCdEf123}. */
    private String userPoolId;

    /** Region hosting the user pool; may differ from the region the service runs in. */
    private String region;

    /**
     * Expected {@code client_id} claim.
     *
     * <p>Optional. When set, tokens issued to a different app client of the same pool are rejected,
     * which matters if the pool ever gains a second client with different scopes.
     */
    private String clientId;

    /** Issuer URL derived from the pool id; also the {@code iss} claim the token must carry. */
    public String issuerUri() {
        return "https://cognito-idp." + region + ".amazonaws.com/" + userPoolId;
    }

    /** Public key set used to verify the RS256 signature. */
    public String jwkSetUri() {
        return issuerUri() + "/.well-known/jwks.json";
    }

    /** True when a pool has actually been configured. */
    public boolean isConfigured() {
        return userPoolId != null && !userPoolId.isBlank()
                && region != null && !region.isBlank();
    }
}
