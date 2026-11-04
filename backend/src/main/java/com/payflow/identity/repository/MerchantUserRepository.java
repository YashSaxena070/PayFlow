package com.payflow.identity.repository;

import com.payflow.identity.domain.MerchantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantUserRepository extends JpaRepository<MerchantUser, UUID> {
    Optional<MerchantUser> findByMerchantIdAndUserId(UUID merchantId, UUID userId);

    @Query("SELECT mu FROM MerchantUser mu JOIN FETCH mu.merchant WHERE mu.user.id = :userId")
    List<MerchantUser> findAllByUserIdWithMerchant(@Param("userId") UUID userId);

    List<MerchantUser> findAllByMerchantId(UUID merchantId);
}

