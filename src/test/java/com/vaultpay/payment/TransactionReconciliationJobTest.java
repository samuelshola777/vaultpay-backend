package com.vaultpay.payment;

import com.vaultpay.withdrawal.WithdrawalRepository;
import com.vaultpay.withdrawal.WithdrawalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionReconciliationJobTest {

    @Mock private PaymentAttemptRepository paymentAttemptRepository;
    @Mock private WithdrawalRepository withdrawalRepository;
    @Mock private PaymentService paymentService;
    @Mock private WithdrawalService withdrawalService;
    private TransactionReconciliationJob job;

    @BeforeEach
    void setUp() {
        job = new TransactionReconciliationJob(
                paymentAttemptRepository, withdrawalRepository, paymentService, withdrawalService
        );
        ReflectionTestUtils.setField(job, "pendingAgeMinutes", 10L);
    }

    @Test
    void reconcilesOldFundingAttempts() {
        PaymentAttempt attempt = PaymentAttempt.builder().reference("VPT-FND-1").build();
        when(paymentAttemptRepository.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(PaymentAttemptStatus.INITIALIZED), any(LocalDateTime.class))).thenReturn(List.of(attempt));
        when(withdrawalRepository.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of());

        job.reconcilePendingTransactions();

        verify(paymentService).verifyFunding("VPT-FND-1");
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
