package com.vaultpay.withdrawal;

import com.vaultpay.activitylog.ActivityLogService;
import com.vaultpay.beneficiary.Beneficiary;
import com.vaultpay.beneficiary.BeneficiaryService;
import com.vaultpay.ledger.LedgerEntryRepository;
import com.vaultpay.notification.NotificationService;
import com.vaultpay.payment.PaystackClient;
import com.vaultpay.payment.PaymentService;
import com.vaultpay.transaction.TransactionStatus;
import com.vaultpay.transaction.WalletTransactionRepository;
import com.vaultpay.transfer.IdempotencyRecordRepository;
import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.utils.emailsenderservice.EmailSenderService;
import com.vaultpay.wallet.Currency;
import com.vaultpay.wallet.Wallet;
import com.vaultpay.wallet.WalletBalance;
import com.vaultpay.wallet.WalletBalanceRepository;
import com.vaultpay.wallet.WalletService;
import com.vaultpay.withdrawal.request.WithdrawalRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {

    @Mock private WithdrawalRepository withdrawalRepository;
    @Mock private BeneficiaryService beneficiaryService;
    @Mock private WalletService walletService;
    @Mock private WalletBalanceRepository balanceRepository;
    @Mock private WalletTransactionRepository transactionRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private PaystackClient paystackClient;
    @Mock private PaymentService paymentService;
    @Mock private NotificationService notificationService;
    @Mock private ActivityLogService activityLogService;
    @Mock private IdempotencyRecordRepository idempotencyRepository;
    @Mock private EmailSenderService emailSenderService;
    private WithdrawalService service;

    @BeforeEach
    void setUp() {
        service = new WithdrawalService(withdrawalRepository, beneficiaryService, walletService, balanceRepository,
                transactionRepository, ledgerEntryRepository, paystackClient, paymentService, notificationService,
                activityLogService, idempotencyRepository, emailSenderService);
        ReflectionTestUtils.setField(service, "withdrawalFee", new BigDecimal("50.00"));
        ReflectionTestUtils.setField(service, "minimumWithdrawal", new BigDecimal("100.00"));
        ReflectionTestUtils.setField(service, "maximumWithdrawal", new BigDecimal("500000.00"));
        ReflectionTestUtils.setField(service, "dailyWithdrawalLimit", new BigDecimal("1000000.00"));
        ReflectionTestUtils.setField(service, "mailFrom", "no-reply@vaultpay.test");
        ReflectionTestUtils.setField(service, "adminEmail", "admin@vaultpay.test");
    }

    @Test
    void emailsAdminWhenWithdrawalIsInitiated() throws Exception {
        User user = User.builder().firstName("Samuel").lastName("Shola")
                .email("samuel@example.com").build();
        user.setId(UUID.randomUUID());
        Wallet wallet = Wallet.builder().user(user).walletNumber("1234567890").build();
        wallet.setId(UUID.randomUUID());
        UUID beneficiaryId = UUID.randomUUID();
        Beneficiary beneficiary = Beneficiary.builder().user(user).accountName("Samuel Shola")
                .accountNumber("0123456789").bankName("Test Bank").bankCode("001")
                .recipientCode("RCP_test").build();
        WalletBalance balance = WalletBalance.builder().wallet(wallet).currency(Currency.NGN)
                .availableBalance(new BigDecimal("1000.00")).heldBalance(BigDecimal.ZERO).build();

        when(idempotencyRepository.findByUserAndIdempotencyKey(user, "withdrawal-1"))
                .thenReturn(Optional.empty());
        when(walletService.getWalletEntity(user)).thenReturn(wallet);
        when(beneficiaryService.getEntity(user, beneficiaryId)).thenReturn(beneficiary);
        when(balanceRepository.findByWalletIdAndCurrencyForUpdate(wallet.getId(), Currency.NGN))
                .thenReturn(Optional.of(balance));
        when(transactionRepository.sumOutgoingSinceForStatuses(eq(wallet), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentService.toMinorUnits(new BigDecimal("100.00"))).thenReturn(10_000L);
        when(paystackClient.initiateTransfer(eq(10_000L), eq("RCP_test"), any(), eq("Emergency funds")))
                .thenReturn(new ObjectMapper().readTree(
                        "{\"status\":true,\"data\":{\"transfer_code\":\"TRF_test\"}}"));
        when(withdrawalRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.withdraw(user, "withdrawal-1",
                new WithdrawalRequest(beneficiaryId, new BigDecimal("100.00"), Currency.NGN,
                        "1234", "Emergency funds"));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSenderService).sendEmail(eq("no-reply@vaultpay.test"), eq("admin@vaultpay.test"),
                subject.capture(), body.capture());
        assertThat(subject.getValue()).isEqualTo("VaultPay withdrawal initiated - " + response.reference());
        assertThat(body.getValue())
                .contains("User: Samuel Shola")
                .contains("Amount: NGN 100.00")
                .contains("Total debit: NGN 150.00")
                .contains("Account: ******6789")
                .contains("Status: " + TransactionStatus.PROCESSING);
    }
}
