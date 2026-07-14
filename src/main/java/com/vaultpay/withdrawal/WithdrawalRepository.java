package com.vaultpay.withdrawal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;
import com.vaultpay.transaction.WalletTransaction;
import com.vaultpay.transaction.TransactionStatus;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WithdrawalRepository extends JpaRepository<Withdrawal, UUID> {

    Optional<Withdrawal> findByReference(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select withdrawal from Withdrawal withdrawal where withdrawal.reference = :reference")
    Optional<Withdrawal> findByReferenceForUpdate(@Param("reference") String reference);

    Optional<Withdrawal> findByTransaction(WalletTransaction transaction);

    List<Withdrawal> findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            TransactionStatus status, LocalDateTime createdBefore
    );
}
