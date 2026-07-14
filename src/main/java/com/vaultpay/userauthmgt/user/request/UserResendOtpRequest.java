package com.vaultpay.userauthmgt.user.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserResendOtpRequest(
        @NotBlank @Email String email
) {
}

