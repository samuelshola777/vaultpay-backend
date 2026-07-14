package com.vaultpay.payment.request;

import com.vaultpay.wallet.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record FundWalletRequest(
        @NotNull @DecimalMin("100.00") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull Currency currency
) {
}

