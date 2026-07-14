package com.vaultpay.payment;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.vaultpay.utils.exception.InvalidInputException;
import com.vaultpay.withdrawal.WithdrawalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class PaystackWebhookService {

    private final ObjectMapper objectMapper;
    private final WebhookEventService webhookEventService;
    private final PaymentService paymentService;
    private final WithdrawalService withdrawalService;

    public void process(String payload) {
        JsonNode event;
        try {
            event = objectMapper.readTree(payload);
        } catch (JacksonException exception) {
            throw new InvalidInputException("Invalid webhook payload");
        }
        String eventType = event.path("event").asText();
        JsonNode data = event.path("data");
        String reference = data.path("reference").asText();
        if (eventType.isBlank() || !data.isObject() || reference.isBlank()) {
            throw new InvalidInputException("Webhook event, data and reference are required");
        }
        String payloadHash = sha256(payload);
        String eventKey = sha256("PAYSTACK|" + eventType + "|" + reference + "|" + payloadHash);
        if (!webhookEventService.register(eventKey, eventType, reference, payloadHash)) {
            return;
        }
        boolean ignored = false;
        try {
            switch (eventType) {
                case "charge.success" -> paymentService.processSuccessfulCharge(data);
                case "transfer.success" -> withdrawalService.complete(reference, data.path("amount").asLong());
                case "transfer.failed" ->
                        withdrawalService.fail(reference, data.path("reason").asText());
                case "transfer.reversed" -> withdrawalService.reverse(reference, data.path("reason").asText());
                default -> ignored = true;
            }
            webhookEventService.markProcessed(eventKey, ignored);
        } catch (RuntimeException exception) {
            webhookEventService.markFailed(eventKey, exception);
            throw exception;
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
