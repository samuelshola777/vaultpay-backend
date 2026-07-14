package com.vaultpay.wallet.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WalletPinSetupRequest(
        @NotBlank @Pattern(regexp = "^\\d{4}$", message = "PIN must contain exactly 4 digits") String pin,
        @NotBlank String confirmPin
) {
}

