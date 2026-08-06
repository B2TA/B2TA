package com.b2ta.common.repository;

import com.b2ta.common.entity.TaUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TaUserRepository extends JpaRepository<TaUser, UUID> {

    /** Resolves the Cognito {@code sub} claim to the local TA record (Requirement 18.1). */
    Optional<TaUser> findByCognitoSub(String cognitoSub);
}
