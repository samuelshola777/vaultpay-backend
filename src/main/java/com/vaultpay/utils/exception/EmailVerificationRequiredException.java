package com.vaultpay.utils.exception;

public class EmailVerificationRequiredException extends RuntimeException {

    private final String email;

    public EmailVerificationRequiredException(String email) {
        super("Email verification is required");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
