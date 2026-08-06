package com.b2ta.api.repository;

import com.b2ta.common.entity.TaUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaUserRepository extends JpaRepository<TaUser, UUID> {

    Optional<TaUser> findByCognitoSub(String cognitoSub);
}
