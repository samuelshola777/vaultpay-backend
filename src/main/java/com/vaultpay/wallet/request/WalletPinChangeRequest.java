package com.vaultpay.wallet.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WalletPinChangeRequest(
        @NotBlank String currentPin,
        @NotBlank @Pattern(regexp = "^\\d{4}$", message = "New PIN must contain exactly 4 digits") String newPin,
        @NotBlank String confirmNewPin
) {
}

