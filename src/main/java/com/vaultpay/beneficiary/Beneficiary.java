package com.vaultpay.beneficiary;

import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@EqualsAndHashCode(callSuper = true, exclude = "user")
@Entity
@Table(
        name = "beneficiaries",
        uniqueConstraints = @UniqueConstraint(
                name = "beneficiaries_user_account_bank_uk", columnNames = {"user_id", "account_number", "bank_code"}
        ),
        indexes = @Index(name = "beneficiaries_user_idx", columnList = "user_id")
)
public class Beneficiary extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String accountName;

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @Column(nullable = false, length = 100)
    private String bankName;

    @Column(name = "bank_code", nullable = false, length = 20)
    private String bankCode;

    @Column(nullable = false, length = 100)
    private String recipientCode;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}

