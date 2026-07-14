package com.vaultpay.ledger;

import com.vaultpay.transaction.WalletTransaction;
import com.vaultpay.utils.BaseEntity;
import com.vaultpay.wallet.Currency;
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
@EqualsAndHashCode(callSuper = true, exclude = "transaction")
@Entity
@Table(name = "ledger_entries", indexes = {
        @Index(name = "ledger_entries_transaction_idx", columnList = "transaction_id"),
        @Index(name = "ledger_entries_account_idx", columnList = "account_reference, created_at")
})
public class LedgerEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private WalletTransaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LedgerEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private LedgerAccountType accountType;

    @Column(nullable = false, length = 100)
    private String accountReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(precision = 19, scale = 2)
    private BigDecimal balanceAfter;
}
