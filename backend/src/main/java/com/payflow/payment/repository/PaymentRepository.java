package com.payflow.payment.repository;

import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<Payment> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);

    Page<Payment> findAllByMerchantIdOrderByCreatedAtDesc(UUID merchantId, Pageable pageable);

    @Query(value = "SELECT * FROM payments WHERE merchant_id = :merchantId " +
           "AND (CAST(:status AS VARCHAR) IS NULL OR status = CAST(:status AS VARCHAR)) " +
           "AND (CAST(:currency AS VARCHAR) IS NULL OR currency = CAST(:currency AS VARCHAR)) " +
           "AND (CAST(:orderId AS VARCHAR) IS NULL OR LOWER(order_id) LIKE CONCAT('%', LOWER(CAST(:orderId AS VARCHAR)), '%')) " +
           "AND (CAST(:fromDate AS TIMESTAMPTZ) IS NULL OR created_at >= CAST(:fromDate AS TIMESTAMPTZ)) " +
           "AND (CAST(:toDate AS TIMESTAMPTZ) IS NULL OR created_at <= CAST(:toDate AS TIMESTAMPTZ))",
           countQuery = "SELECT count(*) FROM payments WHERE merchant_id = :merchantId " +
           "AND (CAST(:status AS VARCHAR) IS NULL OR status = CAST(:status AS VARCHAR)) " +
           "AND (CAST(:currency AS VARCHAR) IS NULL OR currency = CAST(:currency AS VARCHAR)) " +
           "AND (CAST(:orderId AS VARCHAR) IS NULL OR LOWER(order_id) LIKE CONCAT('%', LOWER(CAST(:orderId AS VARCHAR)), '%')) " +
           "AND (CAST(:fromDate AS TIMESTAMPTZ) IS NULL OR created_at >= CAST(:fromDate AS TIMESTAMPTZ)) " +
           "AND (CAST(:toDate AS TIMESTAMPTZ) IS NULL OR created_at <= CAST(:toDate AS TIMESTAMPTZ))",
           nativeQuery = true)
    Page<Payment> searchPayments(
            @Param("merchantId") UUID merchantId,
            @Param("status") String status,
            @Param("currency") String currency,
            @Param("orderId") String orderId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            Pageable pageable
    );

    @Query("SELECT p FROM Payment p WHERE p.status = 'PENDING' AND p.updatedAt <= :staleThreshold")
    List<Payment> findStalePayments(@Param("staleThreshold") Instant staleThreshold);

    @Query("SELECT p.status, COUNT(p), COALESCE(SUM(p.amountMinor), 0) FROM Payment p " +
           "WHERE p.merchant.id = :merchantId AND p.createdAt >= :fromDate AND p.createdAt <= :toDate " +
           "GROUP BY p.status")
    List<Object[]> aggregateMetricsByStatus(
            @Param("merchantId") UUID merchantId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate
    );
}

