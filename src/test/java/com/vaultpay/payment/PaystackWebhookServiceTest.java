package com.vaultpay.payment;

import tools.jackson.databind.ObjectMapper;
import com.vaultpay.withdrawal.WithdrawalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class PaystackWebhookServiceTest {

    @Mock private WebhookEventService webhookEventService;
    @Mock private PaymentService paymentService;
    @Mock private WithdrawalService withdrawalService;
    private PaystackWebhookService service;

    @BeforeEach
    void setUp() {
        service = new PaystackWebhookService(
                new ObjectMapper(), webhookEventService, paymentService, withdrawalService
        );
    }

    @Test
    void processesARegisteredSuccessfulCharge() {
        when(webhookEventService.register(anyString(), eq("charge.success"), eq("VPT-FND-1"), anyString()))
                .thenReturn(true);

        service.process("{\"event\":\"charge.success\",\"data\":{\"reference\":\"VPT-FND-1\",\"amount\":10000}}");

        verify(paymentService).processSuccessfulCharge(org.mockito.ArgumentMatchers.any());
        verify(webhookEventService).markProcessed(anyString(), eq(false));
    }

    @Test
    void ignoresAnAlreadyRegisteredWebhook() {
        when(webhookEventService.register(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        service.process("{\"event\":\"charge.success\",\"data\":{\"reference\":\"VPT-FND-1\"}}");

        verify(paymentService, never()).processSuccessfulCharge(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsAWebhookWithoutAReference() {
        assertThatThrownBy(() -> service.process("{\"event\":\"charge.success\",\"data\":{}}"))
                .isInstanceOf(com.vaultpay.utils.exception.InvalidInputException.class);
        verify(webhookEventService, never()).register(anyString(), anyString(), anyString(), anyString());
    }
}
