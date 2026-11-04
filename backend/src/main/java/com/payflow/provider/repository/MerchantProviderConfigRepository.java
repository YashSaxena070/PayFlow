package com.payflow.provider.repository;

import com.payflow.provider.domain.MerchantProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantProviderConfigRepository extends JpaRepository<MerchantProviderConfig, UUID> {

    List<MerchantProviderConfig> findAllByMerchantId(UUID merchantId);

    @Query("SELECT c FROM MerchantProviderConfig c JOIN FETCH c.provider " +
           "WHERE c.merchant.id = :merchantId AND c.enabled = true AND c.provider.enabled = true " +
           "ORDER BY c.priority ASC")
    List<MerchantProviderConfig> findEnabledByMerchantIdOrdered(@Param("merchantId") UUID merchantId);

    Optional<MerchantProviderConfig> findByMerchantIdAndProviderId(UUID merchantId, UUID providerId);
}

