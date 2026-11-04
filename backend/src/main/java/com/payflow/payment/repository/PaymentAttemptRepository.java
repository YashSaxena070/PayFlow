package com.payflow.payment.repository;

import com.payflow.payment.domain.AttemptStatus;
import com.payflow.payment.domain.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    List<PaymentAttempt> findAllByPaymentIdOrderByAttemptNumberAsc(UUID paymentId);

    Optional<PaymentAttempt> findByProviderReference(String providerReference);

    Optional<PaymentAttempt> findByPaymentIdAndAttemptNumber(UUID paymentId, int attemptNumber);

    @Query("SELECT pa FROM PaymentAttempt pa WHERE pa.status IN ('UNKNOWN', 'PENDING') AND pa.submittedAt <= :staleThreshold")
    List<PaymentAttempt> findStaleAttempts(@Param("staleThreshold") Instant staleThreshold);

    @Query("SELECT pa.provider.code, pa.status, COUNT(pa) FROM PaymentAttempt pa " +
           "WHERE pa.payment.merchant.id = :merchantId AND pa.submittedAt >= :fromDate AND pa.submittedAt <= :toDate " +
           "GROUP BY pa.provider.code, pa.status")
    List<Object[]> aggregateMetricsByProvider(
            @Param("merchantId") UUID merchantId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate
    );
}

