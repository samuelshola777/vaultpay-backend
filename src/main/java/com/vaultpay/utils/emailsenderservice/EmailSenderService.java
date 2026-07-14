package com.vaultpay.utils.emailsenderservice;

public interface EmailSenderService {

    void sendVerificationOtp(String recipient, String firstName, String otp);

    void sendPasswordResetOtp(String recipient, String firstName, String otp);

    void sendEmail(String senderEmail, String recipientEmail, String subject, String body);
}
