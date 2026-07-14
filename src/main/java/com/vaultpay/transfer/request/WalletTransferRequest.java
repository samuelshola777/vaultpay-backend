package com.vaultpay.transfer.request;

import com.vaultpay.wallet.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record WalletTransferRequest(
        @NotBlank @Pattern(regexp = "^\\d{10}$") String destinationWalletNumber,
        @NotNull @DecimalMin(value = "100.00") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull Currency currency,
        @Size(max = 160) String narration,
        @NotBlank @Pattern(regexp = "^\\d{4}$") String pin
) {
}

