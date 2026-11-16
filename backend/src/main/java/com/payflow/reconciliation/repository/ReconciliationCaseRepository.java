package com.payflow.reconciliation.repository;

import com.payflow.reconciliation.domain.ReconciliationCase;
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
public interface ReconciliationCaseRepository extends JpaRepository<ReconciliationCase, UUID> {

    List<ReconciliationCase> findAllByCaseStatusOrderByCreatedAtDesc(String caseStatus);

    Page<ReconciliationCase> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT r FROM ReconciliationCase r WHERE r.attempt.id = :attemptId")
    Optional<ReconciliationCase> findByAttemptId(@Param("attemptId") UUID attemptId);
}
