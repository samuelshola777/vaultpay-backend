package com.vaultpay.withdrawal.request;

import com.vaultpay.wallet.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawalRequest(
        @NotNull UUID beneficiaryId,
        @NotNull @DecimalMin("100.00") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull Currency currency,
        @NotBlank @Pattern(regexp = "^\\d{4}$") String pin,
        @Size(max = 160) String narration
) {
}

