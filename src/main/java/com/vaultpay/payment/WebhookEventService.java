package com.vaultpay.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WebhookEventService {

    private final WebhookEventRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean register(String eventKey, String eventType, String reference, String payloadHash) {
        Optional<WebhookEvent> existing = repository.findByEventKey(eventKey);
        if (existing.isPresent()) {
            WebhookEvent event = existing.get();
            if (event.getStatus() == WebhookEventStatus.PROCESSED
                    || event.getStatus() == WebhookEventStatus.IGNORED) {
                return false;
            }
            if (event.getStatus() == WebhookEventStatus.RECEIVED
                    && event.getUpdatedAt() != null
                    && event.getUpdatedAt().isAfter(LocalDateTime.now().minusMinutes(5))) {
                return false;
            }
            event.setStatus(WebhookEventStatus.RECEIVED);
            event.setAttempts(event.getAttempts() + 1);
            event.setFailureReason(null);
            repository.save(event);
            return true;
        }
        try {
            repository.saveAndFlush(WebhookEvent.builder()
                    .eventKey(eventKey).provider("PAYSTACK").eventType(eventType).reference(reference)
                    .payloadHash(payloadHash).status(WebhookEventStatus.RECEIVED).attempts(1).build());
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(String eventKey, boolean ignored) {
        repository.findByEventKey(eventKey).ifPresent(event -> {
            event.setStatus(ignored ? WebhookEventStatus.IGNORED : WebhookEventStatus.PROCESSED);
            event.setProcessedAt(LocalDateTime.now());
            event.setFailureReason(null);
            repository.save(event);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String eventKey, Exception exception) {
        repository.findByEventKey(eventKey).ifPresent(event -> {
            event.setStatus(WebhookEventStatus.FAILED);
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            event.setFailureReason(message.substring(0, Math.min(message.length(), 500)));
            repository.save(event);
        });
    }
}
