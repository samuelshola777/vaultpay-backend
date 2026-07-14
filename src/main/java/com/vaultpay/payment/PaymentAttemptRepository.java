package com.vaultpay.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    Optional<PaymentAttempt> findByReference(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from PaymentAttempt attempt where attempt.reference = :reference")
    Optional<PaymentAttempt> findByReferenceForUpdate(@Param("reference") String reference);

    List<PaymentAttempt> findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            PaymentAttemptStatus status, LocalDateTime createdBefore
    );
}
