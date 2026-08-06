package com.b2ta.api.security;

import com.b2ta.common.entity.TaUser;
import com.b2ta.common.repository.TaUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maps a Cognito identity to the local {@code ta_user} row that owns data.
 *
 * <p>Accounts are created by an administrator in Cognito (Requirement 18.2), so the pool is the
 * authority on who may sign in. The local row is just the tenant key that foreign keys point at,
 * and is created on first authenticated request rather than by an out-of-band sync, which would
 * otherwise leave a valid token with nowhere to store its data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaUserProvisioningService {

    private static final String DEV_SUB_PREFIX = "dev|";

    private final TaUserRepository taUserRepository;

    /**
     * Resolves the {@code sub} claim of a verified access token to a TA.
     *
     * @param cognitoSub the {@code sub} claim; the stable account identifier
     * @param username   the {@code username} claim, used as the email when provisioning
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TaPrincipal resolveByCognitoSub(String cognitoSub, @Nullable String username) {
        return taUserRepository.findByCognitoSub(cognitoSub)
                .map(this::toPrincipal)
                .orElseGet(() -> {
                    String email = username == null || username.isBlank()
                            ? cognitoSub + "@cognito.local"
                            : username;
                    return toPrincipal(create(cognitoSub, email));
                });
    }

    /**
     * Dev-mode resolution by email.
     *
     * <p>Reachable only when {@code auth.dev-mode} is enabled, which {@code SecurityConfig}
     * restricts to the local profile.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TaPrincipal resolveByDevEmail(String email) {
        String sub = DEV_SUB_PREFIX + email.trim().toLowerCase();
        return taUserRepository.findByCognitoSub(sub)
                .map(this::toPrincipal)
                .orElseGet(() -> toPrincipal(create(sub, email.trim())));
    }

    private TaUser create(String cognitoSub, String email) {
        try {
            TaUser created = taUserRepository.saveAndFlush(TaUser.builder()
                    .cognitoSub(cognitoSub)
                    .email(email)
                    .build());
            log.info("Provisioned TA record {} on first authenticated request", created.getId());
            return created;
        } catch (DataIntegrityViolationException e) {
            // Two concurrent first requests from the same TA race on uq_ta_user_cognito_sub. The
            // loser reads the winner's row instead of failing the request.
            return taUserRepository.findByCognitoSub(cognitoSub)
                    .orElseThrow(() -> e);
        }
    }

    private TaPrincipal toPrincipal(TaUser user) {
        return new TaPrincipal(user.getId(), user.getCognitoSub(), user.getEmail());
    }
}
