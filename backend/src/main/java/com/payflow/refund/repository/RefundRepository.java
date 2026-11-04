package com.payflow.refund.repository;

import com.payflow.refund.domain.Refund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {

    List<Refund> findAllByPaymentId(UUID paymentId);

    Optional<Refund> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<Refund> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);

    Page<Refund> findAllByMerchantIdOrderByCreatedAtDesc(UUID merchantId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(r.amountMinor), 0) FROM Refund r " +
           "WHERE r.payment.id = :paymentId AND r.status IN ('SUCCEEDED', 'PENDING')")
    Long sumActiveRefundAmountByPaymentId(@Param("paymentId") UUID paymentId);
}

