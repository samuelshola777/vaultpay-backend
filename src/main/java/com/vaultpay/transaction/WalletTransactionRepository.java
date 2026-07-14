package com.vaultpay.transaction;

import com.vaultpay.wallet.Wallet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    Optional<WalletTransaction> findByReference(String reference);

    Optional<WalletTransaction> findByProviderReference(String providerReference);

    @Query("select tx from WalletTransaction tx " +
            "where tx.sourceWallet = :wallet or tx.destinationWallet = :wallet")
    Page<WalletTransaction> findAllForWallet(@Param("wallet") Wallet wallet, Pageable pageable);

    @Query("select coalesce(sum(tx.amount + tx.fee), 0) from WalletTransaction tx " +
            "where tx.sourceWallet = :wallet and tx.type = :type " +
            "and tx.status = :status and tx.createdAt >= :from")
    BigDecimal sumOutgoingSince(
            @Param("wallet") Wallet wallet,
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            @Param("from") LocalDateTime from
    );

    @Query("select coalesce(sum(tx.amount + tx.fee), 0) from WalletTransaction tx " +
            "where tx.sourceWallet = :wallet and tx.type = :type " +
            "and tx.status in :statuses and tx.createdAt >= :from")
    BigDecimal sumOutgoingSinceForStatuses(
            @Param("wallet") Wallet wallet,
            @Param("type") TransactionType type,
            @Param("statuses") Collection<TransactionStatus> statuses,
            @Param("from") LocalDateTime from
    );

    long countByStatus(TransactionStatus status);

    long countByStatusIn(Collection<TransactionStatus> statuses);
}
