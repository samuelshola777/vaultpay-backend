package com.vaultpay.transfer;

import com.vaultpay.transaction.WalletTransaction;
import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@EqualsAndHashCode(callSuper = true, exclude = {"user", "transaction"})
@Entity
@Table(
        name = "idempotency_records",
        uniqueConstraints = @UniqueConstraint(name = "idempotency_user_key_uk", columnNames = {"user_id", "idempotency_key"}),
        indexes = @Index(name = "idempotency_user_idx", columnList = "user_id")
)
public class IdempotencyRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private WalletTransaction transaction;
}

