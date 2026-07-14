package com.vaultpay.transaction;

import com.vaultpay.utils.BaseEntity;
import com.vaultpay.wallet.Currency;
import com.vaultpay.wallet.Wallet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"sourceWallet", "destinationWallet"})
@Entity
@Table(name = "wallet_transactions", indexes = {
        @Index(name = "wallet_transactions_reference_idx", columnList = "reference", unique = true),
        @Index(name = "wallet_transactions_source_idx", columnList = "source_wallet_id, created_at"),
        @Index(name = "wallet_transactions_destination_idx", columnList = "destination_wallet_id, created_at")
})
public class WalletTransaction extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal fee = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_wallet_id")
    private Wallet sourceWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_wallet_id")
    private Wallet destinationWallet;

    @Column(length = 160)
    private String narration;

    @Column(length = 100)
    private String providerReference;

    @Column(length = 500)
    private String failureReason;
}
