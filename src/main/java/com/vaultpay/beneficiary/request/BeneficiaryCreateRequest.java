package com.vaultpay.beneficiary.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BeneficiaryCreateRequest(
        @NotBlank @Pattern(regexp = "^\\d{10}$", message = "Account number must contain 10 digits") String accountNumber,
        @NotBlank @Size(max = 20) String bankCode,
        @NotBlank @Size(max = 100) String bankName
) {
}

