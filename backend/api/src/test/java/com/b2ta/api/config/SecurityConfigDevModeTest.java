package com.b2ta.api.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The dev-mode guardrail.
 *
 * <p>{@code auth.dev-mode} authenticates a request from an email header instead of a verified Cognito
 * token. A deployment that shipped with it enabled would expose every TA's grading data to anyone who
 * can reach the load balancer, so {@link SecurityConfig} refuses to start rather than logging a
 * warning. This is the single most consequential branch in the security configuration, so it is tested
 * directly.
 */
class SecurityConfigDevModeTest {

    @ParameterizedTest
    @ValueSource(strings = {"api", "prod", "production", "api,metrics", ""})
    void refusesToStartWithDevModeOutsideLocalOrTest(String activeProfiles) {
        AuthProperties auth = new AuthProperties();
        auth.setDevMode(true);

        assertThatThrownBy(() -> new SecurityConfig(new CognitoProperties(), auth, activeProfiles))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.dev-mode=true")
                .hasMessageContaining("local");
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test", "api,local", "local,test"})
    void allowsDevModeWithTheLocalOrTestProfile(String activeProfiles) {
        AuthProperties auth = new AuthProperties();
        auth.setDevMode(true);

        assertThatCode(() -> new SecurityConfig(new CognitoProperties(), auth, activeProfiles))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"api", "prod", "local", ""})
    void startsInAnyProfileWhenDevModeIsOff(String activeProfiles) {
        AuthProperties auth = new AuthProperties();
        auth.setDevMode(false);

        assertThatCode(() -> new SecurityConfig(new CognitoProperties(), auth, activeProfiles))
                .doesNotThrowAnyException();
    }

    @Test
    void devModeIsOffByDefault() {
        // The default has to be closed: a deployment that forgets to set the property must land on
        // real token validation, not on the bypass.
        assertThat(new AuthProperties().isDevMode()).isFalse();
    }

    @Test
    void noJwtDecoderIsBuiltWithoutAUserPool() {
        AuthProperties auth = new AuthProperties();
        SecurityConfig config = new SecurityConfig(new CognitoProperties(), auth, "api");

        // Absent rather than permissive: CognitoJwtAuthenticationFilter rejects any request that
        // presents a Bearer token when it has no decoder, instead of trusting it.
        assertThat(config.jwtDecoder()).isNull();
    }

    @Test
    void issuerAndJwksUrisAreDerivedFromThePoolId() {
        CognitoProperties cognito = new CognitoProperties();
        cognito.setUserPoolId("us-east-1_AbCdEf123");
        cognito.setRegion("us-east-1");

        assertThat(cognito.isConfigured()).isTrue();
        assertThat(cognito.issuerUri())
                .isEqualTo("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_AbCdEf123");
        assertThat(cognito.jwkSetUri()).isEqualTo(cognito.issuerUri() + "/.well-known/jwks.json");
    }

    @Test
    void anIncompleteCognitoConfigurationCountsAsUnconfigured() {
        CognitoProperties onlyRegion = new CognitoProperties();
        onlyRegion.setRegion("us-east-1");
        assertThat(onlyRegion.isConfigured()).isFalse();

        CognitoProperties onlyPool = new CognitoProperties();
        onlyPool.setUserPoolId("us-east-1_AbCdEf123");
        assertThat(onlyPool.isConfigured()).isFalse();
    }
}
