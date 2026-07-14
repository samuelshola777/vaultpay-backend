package com.vaultpay.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletBalanceRepository extends JpaRepository<WalletBalance, UUID> {

    Optional<WalletBalance> findByWalletAndCurrency(Wallet wallet, Currency currency);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select balance from WalletBalance balance where balance.wallet.id = :walletId and balance.currency = :currency")
    Optional<WalletBalance> findByWalletIdAndCurrencyForUpdate(
            @Param("walletId") UUID walletId,
            @Param("currency") Currency currency
    );
}

