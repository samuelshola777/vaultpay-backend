package com.vaultpay.payment;

import com.vaultpay.transaction.TransactionStatus;
import com.vaultpay.withdrawal.WithdrawalRepository;
import com.vaultpay.withdrawal.WithdrawalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionReconciliationJob {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final PaymentService paymentService;
    private final WithdrawalService withdrawalService;

    @Value("${app.reconciliation.pending-age-minutes:10}")
    private long pendingAgeMinutes;

    @Scheduled(fixedDelayString = "${app.reconciliation.fixed-delay-ms:300000}")
    public void reconcilePendingTransactions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(pendingAgeMinutes);
        paymentAttemptRepository
                .findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(PaymentAttemptStatus.INITIALIZED, threshold)
                .forEach(attempt -> reconcileFunding(attempt.getReference()));
        withdrawalRepository
                .findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(TransactionStatus.PROCESSING, threshold)
                .forEach(withdrawal -> reconcileWithdrawal(withdrawal.getReference()));
    }

    private void reconcileFunding(String reference) {
        try {
            paymentService.verifyFunding(reference);
        } catch (RuntimeException exception) {
            log.warn("Funding reconciliation failed for reference {}: {}", reference, exception.getMessage());
        }
    }

    private void reconcileWithdrawal(String reference) {
        try {
            withdrawalService.reconcile(reference);
        } catch (RuntimeException exception) {
            log.warn("Withdrawal reconciliation failed for reference {}: {}", reference, exception.getMessage());
        }
    }
}
