package com.vaultpay.withdrawal;

import com.vaultpay.beneficiary.Beneficiary;
import com.vaultpay.transaction.TransactionStatus;
import com.vaultpay.transaction.WalletTransaction;
import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"user", "beneficiary", "transaction"})
@Entity
@Table(name = "withdrawals", indexes = {
        @Index(name = "withdrawals_reference_idx", columnList = "reference", unique = true),
        @Index(name = "withdrawals_user_idx", columnList = "user_id, created_at")
})
public class Withdrawal extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beneficiary_id", nullable = false)
    private Beneficiary beneficiary;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private WalletTransaction transaction;

    @Column(nullable = false, unique = true, length = 70)
    private String reference;

    @Column(length = 100)
    private String providerTransferCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;
}

