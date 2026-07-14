package com.vaultpay.withdrawal;

import tools.jackson.databind.JsonNode;
import com.vaultpay.beneficiary.Beneficiary;
import com.vaultpay.beneficiary.BeneficiaryService;
import com.vaultpay.ledger.LedgerAccountType;
import com.vaultpay.ledger.LedgerEntry;
import com.vaultpay.ledger.LedgerEntryRepository;
import com.vaultpay.ledger.LedgerEntryType;
import com.vaultpay.payment.PaystackClient;
import com.vaultpay.payment.PaymentService;
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
import com.vaultpay.wallet.WalletStatus;
import com.vaultpay.withdrawal.request.WithdrawalRequest;
import com.vaultpay.withdrawal.response.WithdrawalResponse;
import com.vaultpay.transfer.IdempotencyRecord;
import com.vaultpay.transfer.IdempotencyRecordRepository;
import com.vaultpay.notification.NotificationService;
import com.vaultpay.notification.NotificationType;
import com.vaultpay.activitylog.ActivityAction;
import com.vaultpay.activitylog.ActivityLogService;
import com.vaultpay.utils.emailsenderservice.EmailSenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawalService {

    private final WithdrawalRepository withdrawalRepository;
    private final BeneficiaryService beneficiaryService;
    private final WalletService walletService;
    private final WalletBalanceRepository balanceRepository;
    private final WalletTransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaystackClient paystackClient;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final EmailSenderService emailSenderService;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.transaction.withdrawal-fee:50.00}")
    private BigDecimal withdrawalFee;

    @Value("${app.transaction.minimum-withdrawal:100.00}")
    private BigDecimal minimumWithdrawal;

    @Value("${app.transaction.maximum-withdrawal:500000.00}")
    private BigDecimal maximumWithdrawal;

    @Value("${app.transaction.daily-withdrawal-limit:1000000.00}")
    private BigDecimal dailyWithdrawalLimit;

    @Transactional
    public WithdrawalResponse withdraw(User user, String idempotencyKey, WithdrawalRequest request) {
        validateIdempotencyKey(idempotencyKey);
        String requestHash = hashRequest(request);
        var existing = idempotencyRepository.findByUserAndIdempotencyKey(user, idempotencyKey.trim());
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash)) {
                throw new InvalidOperationException("Idempotency key was already used for a different request");
            }
            Withdrawal priorWithdrawal = withdrawalRepository.findByTransaction(existing.get().getTransaction())
                    .orElseThrow(() -> new InvalidOperationException("Withdrawal request is still being processed"));
            return map(priorWithdrawal);
        }
        if (request.currency() != Currency.NGN) {
            throw new InvalidInputException("Currency is not currently supported");
        }
        Wallet wallet = walletService.getWalletEntity(user);
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new InvalidOperationException("Wallet is not active");
        }
        walletService.verifyTransactionPin(wallet, request.pin());
        Beneficiary beneficiary = beneficiaryService.getEntity(user, request.beneficiaryId());
        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        validateWithdrawalAmount(amount);
        BigDecimal fee = withdrawalFee.setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal totalDebit = amount.add(fee);

        WalletBalance balance = lockBalance(wallet, request.currency());
        BigDecimal outgoingToday = transactionRepository.sumOutgoingSinceForStatuses(
                wallet, TransactionType.BANK_WITHDRAWAL,
                List.of(TransactionStatus.PROCESSING, TransactionStatus.COMPLETED),
                LocalDateTime.of(LocalDateTime.now().toLocalDate(), LocalTime.MIN)
        );
        if (outgoingToday.add(totalDebit).compareTo(dailyWithdrawalLimit) > 0) {
            throw new InvalidOperationException("Daily withdrawal limit exceeded");
        }
        if (balance.getAvailableBalance().compareTo(totalDebit) < 0) {
            throw new InvalidOperationException("Insufficient wallet balance");
        }
        balance.setAvailableBalance(balance.getAvailableBalance().subtract(totalDebit));
        balance.setHeldBalance(balance.getHeldBalance().add(totalDebit));
        balanceRepository.save(balance);

        String reference = generateReference();
        WalletTransaction transaction = transactionRepository.save(WalletTransaction.builder()
                .reference(reference).type(TransactionType.BANK_WITHDRAWAL).status(TransactionStatus.PROCESSING)
                .currency(request.currency()).amount(amount).fee(fee).sourceWallet(wallet)
                .narration(cleanNarration(request.narration())).build());

        idempotencyRepository.saveAndFlush(IdempotencyRecord.builder()
                .user(user).idempotencyKey(idempotencyKey.trim()).requestHash(requestHash)
                .transaction(transaction).build());

        JsonNode provider = paystackClient.initiateTransfer(
                paymentService.toMinorUnits(amount), beneficiary.getRecipientCode(), reference, transaction.getNarration());
        String transferCode = provider.path("data").path("transfer_code").asText();
        if (transferCode.isBlank()) {
            throw new InvalidOperationException("Payment provider did not return a transfer code");
        }

        Withdrawal saved = withdrawalRepository.save(Withdrawal.builder()
                .user(user).beneficiary(beneficiary).transaction(transaction).reference(reference)
                .providerTransferCode(transferCode).status(TransactionStatus.PROCESSING).build());
        notificationService.create(user, NotificationType.WITHDRAWAL, "Withdrawal processing",
                "Your NGN " + amount.toPlainString() + " withdrawal is being processed.");
        activityLogService.log(user.getId(), ActivityAction.WITHDREW, "WITHDRAWAL",
                "Withdrawal initiated: " + reference);
        sendAdminWithdrawalAlert(user, beneficiary, transaction);
        return map(saved);
    }

    @Transactional
    public void complete(String reference, long providerAmount) {
        Withdrawal withdrawal = withdrawalRepository.findByReferenceForUpdate(reference).orElse(null);
        if (withdrawal == null || withdrawal.getStatus() != TransactionStatus.PROCESSING) {
            return;
        }
        WalletTransaction transaction = withdrawal.getTransaction();
        if (providerAmount != paymentService.toMinorUnits(transaction.getAmount())) {
            throw new InvalidOperationException("Withdrawal amount does not match");
        }
        BigDecimal totalDebit = transaction.getAmount().add(transaction.getFee());
        WalletBalance balance = lockBalance(transaction.getSourceWallet(), transaction.getCurrency());
        if (balance.getHeldBalance().compareTo(totalDebit) < 0) {
            throw new InvalidOperationException("Withdrawal hold is inconsistent");
        }
        balance.setHeldBalance(balance.getHeldBalance().subtract(totalDebit));
        balanceRepository.save(balance);

        transaction.setStatus(TransactionStatus.COMPLETED);
        withdrawal.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(transaction);
        withdrawalRepository.save(withdrawal);

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transaction(transaction).entryType(LedgerEntryType.DEBIT).accountType(LedgerAccountType.WALLET)
                .accountReference(transaction.getSourceWallet().getWalletNumber()).currency(transaction.getCurrency())
                .amount(totalDebit).balanceAfter(balance.getAvailableBalance()).build());
        ledgerEntryRepository.save(LedgerEntry.builder()
                .transaction(transaction).entryType(LedgerEntryType.CREDIT)
                .accountType(LedgerAccountType.PAYSTACK_PAYOUT_CLEARING)
                .accountReference("PAYSTACK-PAYOUT-NGN").currency(transaction.getCurrency())
                .amount(transaction.getAmount()).build());
        if (transaction.getFee().signum() > 0) {
            ledgerEntryRepository.save(LedgerEntry.builder()
                    .transaction(transaction).entryType(LedgerEntryType.CREDIT)
                    .accountType(LedgerAccountType.PLATFORM_FEE).accountReference("PLATFORM-FEE-NGN")
                    .currency(transaction.getCurrency()).amount(transaction.getFee()).build());
        }
        notificationService.create(withdrawal.getUser(), NotificationType.WITHDRAWAL, "Withdrawal completed",
                "Your withdrawal of NGN " + transaction.getAmount().toPlainString() + " was completed.");
    }

    @Transactional
    public void fail(String reference, String reason) {
        Withdrawal withdrawal = withdrawalRepository.findByReferenceForUpdate(reference).orElse(null);
        if (withdrawal == null || withdrawal.getStatus() != TransactionStatus.PROCESSING) {
            return;
        }
        WalletTransaction transaction = withdrawal.getTransaction();
        BigDecimal totalDebit = transaction.getAmount().add(transaction.getFee());
        WalletBalance balance = lockBalance(transaction.getSourceWallet(), transaction.getCurrency());
        if (balance.getHeldBalance().compareTo(totalDebit) < 0) {
            throw new InvalidOperationException("Withdrawal hold is inconsistent");
        }
        balance.setHeldBalance(balance.getHeldBalance().subtract(totalDebit));
        balance.setAvailableBalance(balance.getAvailableBalance().add(totalDebit));
        balanceRepository.save(balance);

        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setFailureReason(reason == null ? "Provider rejected withdrawal" : reason);
        withdrawal.setStatus(TransactionStatus.FAILED);
        transactionRepository.save(transaction);
        withdrawalRepository.save(withdrawal);
        notificationService.create(withdrawal.getUser(), NotificationType.WITHDRAWAL, "Withdrawal failed",
                "Your withdrawal failed and the held amount was returned to your wallet.");
    }

    @Transactional
    public void reverse(String reference, String reason) {
        Withdrawal withdrawal = withdrawalRepository.findByReferenceForUpdate(reference).orElse(null);
        if (withdrawal == null || withdrawal.getStatus() == TransactionStatus.REVERSED
                || withdrawal.getStatus() == TransactionStatus.FAILED) {
            return;
        }
        WalletTransaction transaction = withdrawal.getTransaction();
        BigDecimal totalDebit = transaction.getAmount().add(transaction.getFee());
        WalletBalance balance = lockBalance(transaction.getSourceWallet(), transaction.getCurrency());

        if (withdrawal.getStatus() == TransactionStatus.PROCESSING) {
            if (balance.getHeldBalance().compareTo(totalDebit) < 0) {
                throw new InvalidOperationException("Withdrawal hold is inconsistent");
            }
            balance.setHeldBalance(balance.getHeldBalance().subtract(totalDebit));
        } else if (withdrawal.getStatus() == TransactionStatus.COMPLETED) {
            ledgerEntryRepository.save(LedgerEntry.builder()
                    .transaction(transaction).entryType(LedgerEntryType.CREDIT).accountType(LedgerAccountType.WALLET)
                    .accountReference(transaction.getSourceWallet().getWalletNumber()).currency(transaction.getCurrency())
                    .amount(totalDebit).balanceAfter(balance.getAvailableBalance().add(totalDebit)).build());
            ledgerEntryRepository.save(LedgerEntry.builder()
                    .transaction(transaction).entryType(LedgerEntryType.DEBIT)
                    .accountType(LedgerAccountType.PAYSTACK_PAYOUT_CLEARING)
                    .accountReference("PAYSTACK-PAYOUT-NGN").currency(transaction.getCurrency())
                    .amount(transaction.getAmount()).build());
            if (transaction.getFee().signum() > 0) {
                ledgerEntryRepository.save(LedgerEntry.builder()
                        .transaction(transaction).entryType(LedgerEntryType.DEBIT)
                        .accountType(LedgerAccountType.PLATFORM_FEE).accountReference("PLATFORM-FEE-NGN")
                        .currency(transaction.getCurrency()).amount(transaction.getFee()).build());
            }
        } else {
            return;
        }

        balance.setAvailableBalance(balance.getAvailableBalance().add(totalDebit));
        balanceRepository.save(balance);
        transaction.setStatus(TransactionStatus.REVERSED);
        transaction.setFailureReason(reason == null || reason.isBlank() ? "Provider reversed withdrawal" : reason);
        withdrawal.setStatus(TransactionStatus.REVERSED);
        transactionRepository.save(transaction);
        withdrawalRepository.save(withdrawal);
        notificationService.create(withdrawal.getUser(), NotificationType.WITHDRAWAL, "Withdrawal reversed",
                "Your withdrawal was reversed and the amount was returned to your wallet.");
    }

    @Transactional
    public void reconcile(String reference) {
        Withdrawal withdrawal = withdrawalRepository.findByReferenceForUpdate(reference).orElse(null);
        if (withdrawal == null || withdrawal.getStatus() != TransactionStatus.PROCESSING) {
            return;
        }
        JsonNode response = paystackClient.verifyTransfer(reference);
        JsonNode data = response.path("data");
        String providerStatus = data.path("status").asText();
        if ("success".equalsIgnoreCase(providerStatus)) {
            complete(reference, data.path("amount").asLong());
        } else if ("failed".equalsIgnoreCase(providerStatus)) {
            fail(reference, data.path("reason").asText("Provider rejected withdrawal"));
        } else if ("reversed".equalsIgnoreCase(providerStatus)) {
            reverse(reference, data.path("reason").asText("Provider reversed withdrawal"));
        }
    }

    private WalletBalance lockBalance(Wallet wallet, Currency currency) {
        return balanceRepository.findByWalletIdAndCurrencyForUpdate(wallet.getId(), currency)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet balance not found"));
    }

    private WithdrawalResponse map(Withdrawal value) {
        WalletTransaction transaction = value.getTransaction();
        Beneficiary beneficiary = value.getBeneficiary();
        return new WithdrawalResponse(value.getId(), value.getReference(), value.getStatus(),
                transaction.getAmount(), transaction.getFee(), transaction.getCurrency(), beneficiary.getAccountName(),
                beneficiary.getAccountNumber(), beneficiary.getBankName(), value.getCreatedAt());
    }

    private String generateReference() {
        return "VPT-WDL-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private String cleanNarration(String narration) {
        return narration == null || narration.isBlank() ? "Bank withdrawal" : narration.trim();
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new InvalidInputException("A valid Idempotency-Key header is required");
        }
    }

    private String hashRequest(WithdrawalRequest request) {
        String canonical = "BANK_WITHDRAWAL|" + request.beneficiaryId() + "|"
                + request.amount().setScale(2).toPlainString() + "|" + request.currency()
                + "|" + cleanNarration(request.narration());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void validateWithdrawalAmount(BigDecimal amount) {
        if (amount.compareTo(minimumWithdrawal) < 0 || amount.compareTo(maximumWithdrawal) > 0) {
            throw new InvalidInputException("Withdrawal amount is outside the allowed limits");
        }
    }

    private void sendAdminWithdrawalAlert(User user, Beneficiary beneficiary, WalletTransaction transaction) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("Admin withdrawal email was not sent because app.admin.email is not configured");
            return;
        }

        String body = String.join(System.lineSeparator(),
                "A VaultPay withdrawal has been initiated.",
                "",
                "Reference: " + transaction.getReference(),
                "User: " + user.getFirstName() + " " + user.getLastName(),
                "User email: " + user.getEmail(),
                "Amount: " + transaction.getCurrency() + " " + transaction.getAmount().toPlainString(),
                "Fee: " + transaction.getCurrency() + " " + transaction.getFee().toPlainString(),
                "Total debit: " + transaction.getCurrency() + " "
                        + transaction.getAmount().add(transaction.getFee()).toPlainString(),
                "Beneficiary: " + beneficiary.getAccountName(),
                "Bank: " + beneficiary.getBankName(),
                "Account: " + maskAccountNumber(beneficiary.getAccountNumber()),
                "Status: " + transaction.getStatus());

        try {
            emailSenderService.sendEmail(mailFrom, adminEmail,
                    "VaultPay withdrawal initiated - " + transaction.getReference(), body);
        } catch (RuntimeException exception) {
            log.error("Could not send admin withdrawal alert for reference {}: {}",
                    transaction.getReference(), exception.getMessage(), exception);
        }
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return accountNumber;
        }
        return "*".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
    }
}
