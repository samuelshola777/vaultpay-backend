package com.vaultpay.utils.emailsenderservice;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Year;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpEmailSenderService implements EmailSenderService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.otp.expiration-minutes:10}")
    private long otpExpirationMinutes;

    @Override
    @Async
    public void sendVerificationOtp(
            String recipient,
            String firstName,
            String otp
    ) {
        Context context = createContext(firstName, otp);
        String htmlBody = templateEngine.process(
                "vaultpay-verification-otp-template",
                context
        );

        sendHtmlEmail(
                from,
                recipient,
                "Verify your VaultPay email",
                htmlBody
        );
    }

    @Override
    @Async
    public void sendPasswordResetOtp(
            String recipient,
            String firstName,
            String otp
    ) {
        Context context = createContext(firstName, otp);
        String htmlBody = templateEngine.process(
                "vaultpay-password-reset-template",
                context
        );

        sendHtmlEmail(
                from,
                recipient,
                "Reset your VaultPay password",
                htmlBody
        );
    }

    @Override
    public void sendEmail(
            String senderEmail,
            String recipientEmail,
            String subject,
            String body
    ) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(body, false);

            mailSender.send(message);
        } catch (Exception exception) {
            log.error(
                    "Could not send email to {}: {}",
                    recipientEmail,
                    exception.getMessage(),
                    exception
            );
        }
    }

    private void sendHtmlEmail(
            String senderEmail,
            String recipientEmail,
            String subject,
            String htmlBody
    ) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);

            log.info("Email sent successfully to {}", recipientEmail);
        } catch (Exception exception) {
            // Logging the error prevents SMTP failure from stopping registration.
            log.error(
                    "Could not send email to {}: {}",
                    recipientEmail,
                    exception.getMessage(),
                    exception
            );
        }
    }

    private Context createContext(String firstName, String otp) {
        Context context = new Context(Locale.getDefault());

        context.setVariable(
                "firstName",
                firstName == null || firstName.isBlank()
                        ? "there"
                        : firstName
        );
        context.setVariable("otp", otp);
        context.setVariable("otpExpirationMinutes", otpExpirationMinutes);
        context.setVariable("year", Year.now().getValue());

        return context;
    }
}