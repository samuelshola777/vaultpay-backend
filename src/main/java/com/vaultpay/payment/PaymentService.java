package com.vaultpay.payment;

import tools.jackson.databind.JsonNode;
import com.vaultpay.ledger.LedgerAccountType;
import com.vaultpay.ledger.LedgerEntry;
import com.vaultpay.ledger.LedgerEntryRepository;
import com.vaultpay.ledger.LedgerEntryType;
import com.vaultpay.payment.request.FundWalletRequest;
import com.vaultpay.payment.response.PaymentInitializationResponse;
import com.vaultpay.payment.response.PaymentVerificationResponse;
import com.vaultpay.transaction.TransactionStatus;
import com.vaultpay.transaction.TransactionType;
import com.vaultpay.transaction.WalletTransaction;
import com.vaultpay.transaction.WalletTransactionRepository;
import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.utils.exception.InvalidInputException;
import com.vaultpay.utils.exception.InvalidOperationException;
import com.vaultpay.utils.exception.ResourceNotFoundException;
import com.vaultpay.wallet.Currency;
import com.vaultpay.wallet.Wallet;
import com.vaultpay.wallet.WalletBalance;
import com.vaultpay.wallet.WalletBalanceRepository;
import com.vaultpay.wallet.WalletService;
import com.vaultpay.notification.NotificationService;
import com.vaultpay.notification.NotificationType;
import com.vaultpay.activitylog.ActivityAction;
import com.vaultpay.activitylog.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaystackClient paystackClient;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final WalletService walletService;
    private final WalletBalanceRepository balanceRepository;
    private final WalletTransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;

    @Value("${app.transaction.minimum-funding:100.00}")
    private BigDecimal minimumFunding;

    @Value("${app.transaction.maximum-funding:2000000.00}")
    private BigDecimal maximumFunding;

    @Transactional
    public PaymentInitializationResponse initializeFunding(User user, FundWalletRequest request) {
        if (request.currency() != Currency.NGN) {
            throw new InvalidInputException("Currency is not currently supported");
        }
        Wallet wallet = walletService.getWalletEntity(user);
        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        validateFundingAmount(amount);
        String reference = generateReference("FND");
        JsonNode response = paystackClient.initializeTransaction(user.getEmail(), toMinorUnits(amount), reference);
        JsonNode data = response.path("data");
        String authorizationUrl = data.path("authorization_url").asText();
        String accessCode = data.path("access_code").asText();
        if (authorizationUrl.isBlank() || accessCode.isBlank()) {
            throw new InvalidOperationException("Payment provider returned an incomplete checkout response");
        }

        PaymentAttempt attempt = paymentAttemptRepository.save(PaymentAttempt.builder()
                .user(user).wallet(wallet).reference(reference).amount(amount).currency(request.currency())
                .status(PaymentAttemptStatus.INITIALIZED)
                .authorizationUrl(authorizationUrl)
                .accessCode(accessCode).build());

        return new PaymentInitializationResponse(
                attempt.getReference(), attempt.getAmount(), attempt.getCurrency().name(),
                attempt.getAuthorizationUrl(), attempt.getAccessCode()
        );
    }

    @Transactional
    public PaymentVerificationResponse verifyFunding(User user, String reference) {
        PaymentAttempt attempt = paymentAttemptRepository.findByReference(reference)
                .filter(value -> value.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Payment attempt not found"));
        if (attempt.getStatus() != PaymentAttemptStatus.COMPLETED) {
            verifyFunding(reference);
            attempt = paymentAttemptRepository.findByReference(reference)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment attempt not found"));
        }
        return mapVerification(attempt);
    }

    @Transactional
    public void verifyFunding(String reference) {
        PaymentAttempt attempt = paymentAttemptRepository.findByReferenceForUpdate(reference).orElse(null);
        if (attempt == null || attempt.getStatus() == PaymentAttemptStatus.COMPLETED) {
            return;
        }
        JsonNode response = paystackClient.verifyTransaction(reference);
        JsonNode data = response.path("data");
        String providerStatus = data.path("status").asText();
        if ("success".equalsIgnoreCase(providerStatus)) {
            processSuccessfulCharge(data);
        } else if ("failed".equalsIgnoreCase(providerStatus)
                || "abandoned".equalsIgnoreCase(providerStatus)
                || "reversed".equalsIgnoreCase(providerStatus)) {
            attempt.setStatus(PaymentAttemptStatus.FAILED);
            paymentAttemptRepository.save(attempt);
        }
    }

    @Transactional
    public void processSuccessfulCharge(JsonNode data) {
        String reference = data.path("reference").asText();
        PaymentAttempt attempt = paymentAttemptRepository.findByReferenceForUpdate(reference).orElse(null);
        if (attempt == null) {
            return;
        }
        if (attempt.getStatus() == PaymentAttemptStatus.COMPLETED) {
            return;
        }
        long paidMinorUnits = data.path("amount").asLong(-1);
        String currency = data.path("currency").asText();
        if (paidMinorUnits != toMinorUnits(attempt.getAmount()) || !attempt.getCurrency().name().equals(currency)) {
            throw new InvalidOperationException("Payment amount or currency does not match");
        }

        WalletBalance balance = balanceRepository
                .findByWalletIdAndCurrencyForUpdate(attempt.getWallet().getId(), attempt.getCurrency())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet balance not found"));
        balance.setAvailableBalance(balance.getAvailableBalance().add(attempt.getAmount()));
        balanceRepository.save(balance);

        WalletTransaction transaction = transactionRepository.save(WalletTransaction.builder()
                .reference(reference).providerReference(String.valueOf(data.path("id").asLong()))
                .type(TransactionType.WALLET_FUNDING).status(TransactionStatus.COMPLETED)
                .currency(attempt.getCurrency()).amount(attempt.getAmount()).fee(BigDecimal.ZERO)
                .destinationWallet(attempt.getWallet()).narration("Wallet funding").build());

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transaction(transaction).entryType(LedgerEntryType.DEBIT)
                .accountType(LedgerAccountType.PAYSTACK_CLEARING).accountReference("PAYSTACK-CLEARING-NGN")
                .currency(attempt.getCurrency()).amount(attempt.getAmount()).build());
        ledgerEntryRepository.save(LedgerEntry.builder()
                .transaction(transaction).entryType(LedgerEntryType.CREDIT)
                .accountType(LedgerAccountType.WALLET).accountReference(attempt.getWallet().getWalletNumber())
                .currency(attempt.getCurrency()).amount(attempt.getAmount())
                .balanceAfter(balance.getAvailableBalance()).build());

        attempt.setStatus(PaymentAttemptStatus.COMPLETED);
        paymentAttemptRepository.save(attempt);
        notificationService.create(attempt.getUser(), NotificationType.FUNDING, "Wallet funded",
                "Your wallet was credited with NGN " + attempt.getAmount().toPlainString() + ".");
        activityLogService.log(attempt.getUser().getId(), ActivityAction.FUNDED, "PAYMENT",
                "Wallet funding completed: " + reference);
    }

    public long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private String generateReference(String prefix) {
        return "VPT-" + prefix + "-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private PaymentVerificationResponse mapVerification(PaymentAttempt attempt) {
        return new PaymentVerificationResponse(
                attempt.getReference(), attempt.getAmount(), attempt.getCurrency(), attempt.getStatus()
        );
    }

    private void validateFundingAmount(BigDecimal amount) {
        if (amount.compareTo(minimumFunding) < 0 || amount.compareTo(maximumFunding) > 0) {
            throw new InvalidInputException("Funding amount is outside the allowed limits");
        }
    }
}
