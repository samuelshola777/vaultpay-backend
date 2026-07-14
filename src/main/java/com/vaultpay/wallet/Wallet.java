package com.vaultpay.wallet;

import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.utils.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"user", "balances"})
@Entity
@Table(name = "wallets", indexes = {
        @Index(name = "wallets_wallet_number_idx", columnList = "wallet_number", unique = true),
        @Index(name = "wallets_user_id_idx", columnList = "user_id", unique = true)
})
public class Wallet extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "wallet_number", nullable = false, unique = true, length = 10)
    private String walletNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WalletStatus status = WalletStatus.ACTIVE;

    @Column
    private String pinHash;

    @Column(nullable = false)
    @Builder.Default
    private boolean pinSet = false;

    @OneToMany(mappedBy = "wallet", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WalletBalance> balances = new ArrayList<>();

    public void addBalance(WalletBalance balance) {
        balances.add(balance);
        balance.setWallet(this);
    }
}

