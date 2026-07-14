package com.vaultpay.wallet;

import com.vaultpay.userauthmgt.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUser(User user);

    Optional<Wallet> findByWalletNumber(String walletNumber);

    boolean existsByUser(User user);

    boolean existsByWalletNumber(String walletNumber);

    long countByStatus(WalletStatus status);
}
