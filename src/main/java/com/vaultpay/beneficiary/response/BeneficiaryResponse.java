package com.vaultpay.beneficiary.response;

import java.util.UUID;

public record BeneficiaryResponse(
        UUID id,
        String accountName,
        String accountNumber,
        String bankName,
        String bankCode
) {
}

