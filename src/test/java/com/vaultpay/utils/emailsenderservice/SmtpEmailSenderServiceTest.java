package com.vaultpay.utils.emailsenderservice;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderServiceTest {

    @Mock private JavaMailSender mailSender;
    private SmtpEmailSenderService service;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        service = new SmtpEmailSenderService(mailSender, templateEngine);
        ReflectionTestUtils.setField(service, "from", "security@vaultpay.test");
        ReflectionTestUtils.setField(service, "otpExpirationMinutes", 10L);
    }

    @Test
    void rendersAndSendsPasswordResetTemplate() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.sendPasswordResetOtp("samuel@example.com", "Samuel", "123456");

        verify(mailSender).send(message);
        assertThat(message.getSubject()).isEqualTo("Reset your VaultPay password");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("samuel@example.com");
        MimeMultipart content = (MimeMultipart) message.getContent();
        MimeMultipart alternatives = (MimeMultipart) content.getBodyPart(0).getContent();
        String html = (String) alternatives.getBodyPart(0).getContent();
        assertThat(html)
                .contains("Hello <strong")
                .contains("Samuel")
                .contains("123456")
                .contains("Expires in <strong")
                .contains("10");
    }
}
