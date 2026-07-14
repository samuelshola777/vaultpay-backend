package com.vaultpay.payment;

import tools.jackson.databind.ObjectMapper;
import com.vaultpay.activitylog.ActivityLogService;
import com.vaultpay.ledger.LedgerEntryRepository;
import com.vaultpay.notification.NotificationService;
import com.vaultpay.payment.request.FundWalletRequest;
import com.vaultpay.transaction.WalletTransactionRepository;
import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.wallet.Currency;
import com.vaultpay.wallet.Wallet;
import com.vaultpay.wallet.WalletBalanceRepository;
import com.vaultpay.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaystackClient paystackClient;
    @Mock private PaymentAttemptRepository paymentAttemptRepository;
    @Mock private WalletService walletService;
    @Mock private WalletBalanceRepository balanceRepository;
    @Mock private WalletTransactionRepository transactionRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private NotificationService notificationService;
    @Mock private ActivityLogService activityLogService;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(paystackClient, paymentAttemptRepository, walletService, balanceRepository,
                transactionRepository, ledgerEntryRepository, notificationService, activityLogService);
        ReflectionTestUtils.setField(service, "minimumFunding", new BigDecimal("100.00"));
        ReflectionTestUtils.setField(service, "maximumFunding", new BigDecimal("2000000.00"));
    }

    @Test
    void initializesFundingInMinorUnits() throws Exception {
        User user = User.builder().email("samuel@example.com").build();
        Wallet wallet = Wallet.builder().walletNumber("1234567890").user(user).build();
        when(walletService.getWalletEntity(user)).thenReturn(wallet);
        when(paystackClient.initializeTransaction(eq("samuel@example.com"), eq(10_000L), any()))
                .thenReturn(new ObjectMapper().readTree(
                        "{\"status\":true,\"data\":{\"authorization_url\":\"https://checkout.test\",\"access_code\":\"abc\"}}"
                ));
        when(paymentAttemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.initializeFunding(user, new FundWalletRequest(new BigDecimal("100.00"), Currency.NGN));

        assertThat(response.amount()).isEqualByComparingTo("100.00");
        assertThat(response.authorizationUrl()).isEqualTo("https://checkout.test");
    }

    @Test
    void rejectsAnIncompleteProviderCheckoutResponse() throws Exception {
        User user = User.builder().email("samuel@example.com").build();
        Wallet wallet = Wallet.builder().walletNumber("1234567890").user(user).build();
        when(walletService.getWalletEntity(user)).thenReturn(wallet);
        when(paystackClient.initializeTransaction(eq("samuel@example.com"), eq(10_000L), any()))
                .thenReturn(new ObjectMapper().readTree("{\"status\":true,\"data\":{}}"));

        assertThatThrownBy(() -> service.initializeFunding(
                user, new FundWalletRequest(new BigDecimal("100.00"), Currency.NGN)))
                .isInstanceOf(com.vaultpay.utils.exception.InvalidOperationException.class);
    }
}
