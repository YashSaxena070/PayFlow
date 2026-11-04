package com.payflow.identity.repository;

import com.payflow.identity.domain.ApiCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiCredentialRepository extends JpaRepository<ApiCredential, UUID> {
    Optional<ApiCredential> findBySecretHash(String secretHash);
    List<ApiCredential> findAllByMerchantIdAndRevokedAtIsNull(UUID merchantId);
    List<ApiCredential> findAllByMerchantId(UUID merchantId);
}

