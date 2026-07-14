package com.vaultpay.transfer;

import com.vaultpay.ledger.LedgerAccountType;
import com.vaultpay.ledger.LedgerEntry;
import com.vaultpay.ledger.LedgerEntryRepository;
import com.vaultpay.ledger.LedgerEntryType;
import com.vaultpay.transaction.TransactionService;
import com.vaultpay.transaction.TransactionStatus;
import com.vaultpay.transaction.TransactionType;
import com.vaultpay.transaction.WalletTransaction;
import com.vaultpay.transaction.WalletTransactionRepository;
import com.vaultpay.transaction.response.TransactionResponse;
import com.vaultpay.transfer.request.WalletTransferRequest;
import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.utils.exception.InvalidInputException;
import com.vaultpay.utils.exception.InvalidOperationException;
import com.vaultpay.wallet.Currency;
import com.vaultpay.wallet.Wallet;
import com.vaultpay.wallet.WalletBalance;
import com.vaultpay.wallet.WalletBalanceRepository;
import com.vaultpay.wallet.WalletService;
import com.vaultpay.wallet.WalletStatus;
import com.vaultpay.notification.NotificationService;
import com.vaultpay.notification.NotificationType;
import com.vaultpay.activitylog.ActivityAction;
import com.vaultpay.activitylog.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final WalletService walletService;
    private final WalletBalanceRepository balanceRepository;
    private final WalletTransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final TransactionService transactionService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;

    @Value("${app.transaction.daily-transfer-limit:1000000.00}")
    private BigDecimal dailyTransferLimit;

    @Value("${app.transaction.minimum-transfer:100.00}")
    private BigDecimal minimumTransfer;

    @Value("${app.transaction.maximum-transfer:500000.00}")
    private BigDecimal maximumTransfer;

    @Transactional
    public TransactionResponse transfer(User user, String idempotencyKey, WalletTransferRequest request) {
        validateIdempotencyKey(idempotencyKey);
        idempotencyKey = idempotencyKey.trim();
        String requestHash = hashRequest(request);

        var existing = idempotencyRepository.findByUserAndIdempotencyKey(user, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash)) {
                throw new InvalidOperationException("Idempotency key was already used for a different request");
            }
            return transactionService.mapToResponse(existing.get().getTransaction());
        }

        if (request.currency() != Currency.NGN) {
            throw new InvalidInputException("Currency is not currently supported");
        }

        Wallet sourceWallet = walletService.getWalletEntity(user);
        Wallet destinationWallet = walletService.findByWalletNumber(request.destinationWalletNumber());
        validateWallets(sourceWallet, destinationWallet);
        walletService.verifyTransactionPin(sourceWallet, request.pin());

        BigDecimal amount = request.amount().setScale(2);
        if (amount.compareTo(minimumTransfer) < 0 || amount.compareTo(maximumTransfer) > 0) {
            throw new InvalidInputException("Transfer amount is outside the allowed limits");
        }
        WalletBalance firstLocked;
        WalletBalance secondLocked;
        if (sourceWallet.getId().compareTo(destinationWallet.getId()) < 0) {
            firstLocked = lockBalance(sourceWallet, request.currency());
            secondLocked = lockBalance(destinationWallet, request.currency());
        } else {
            firstLocked = lockBalance(destinationWallet, request.currency());
            secondLocked = lockBalance(sourceWallet, request.currency());
        }
        WalletBalance sourceBalance = firstLocked.getWallet().getId().equals(sourceWallet.getId())
                ? firstLocked : secondLocked;
        WalletBalance destinationBalance = firstLocked.getWallet().getId().equals(destinationWallet.getId())
                ? firstLocked : secondLocked;
        BigDecimal outgoingToday = transactionRepository.sumOutgoingSince(
                sourceWallet, TransactionType.WALLET_TRANSFER, TransactionStatus.COMPLETED,
                LocalDateTime.of(LocalDateTime.now().toLocalDate(), LocalTime.MIN)
        );
        if (outgoingToday.add(amount).compareTo(dailyTransferLimit) > 0) {
            throw new InvalidOperationException("Daily transfer limit exceeded");
        }
        if (sourceBalance.getAvailableBalance().compareTo(amount) < 0) {
            throw new InvalidOperationException("Insufficient wallet balance");
        }

        sourceBalance.setAvailableBalance(sourceBalance.getAvailableBalance().subtract(amount));
        destinationBalance.setAvailableBalance(destinationBalance.getAvailableBalance().add(amount));
        balanceRepository.save(sourceBalance);
        balanceRepository.save(destinationBalance);

        WalletTransaction transaction = transactionRepository.save(WalletTransaction.builder()
                .reference(generateReference("TRF"))
                .type(TransactionType.WALLET_TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .currency(request.currency())
                .amount(amount)
                .fee(BigDecimal.ZERO)
                .sourceWallet(sourceWallet)
                .destinationWallet(destinationWallet)
                .narration(cleanNarration(request.narration()))
                .build());

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transaction(transaction).entryType(LedgerEntryType.DEBIT).accountType(LedgerAccountType.WALLET)
                .accountReference(sourceWallet.getWalletNumber()).currency(request.currency()).amount(amount)
                .balanceAfter(sourceBalance.getAvailableBalance()).build());
        ledgerEntryRepository.save(LedgerEntry.builder()
                .transaction(transaction).entryType(LedgerEntryType.CREDIT).accountType(LedgerAccountType.WALLET)
                .accountReference(destinationWallet.getWalletNumber()).currency(request.currency()).amount(amount)
                .balanceAfter(destinationBalance.getAvailableBalance()).build());

        idempotencyRepository.save(IdempotencyRecord.builder()
                .user(user).idempotencyKey(idempotencyKey).requestHash(requestHash).transaction(transaction).build());
        notificationService.create(user, NotificationType.TRANSFER, "Transfer successful",
                "NGN " + amount.toPlainString() + " was sent to wallet " + destinationWallet.getWalletNumber() + ".");
        notificationService.create(destinationWallet.getUser(), NotificationType.TRANSFER, "Wallet credited",
                "NGN " + amount.toPlainString() + " was received from wallet " + sourceWallet.getWalletNumber() + ".");
        activityLogService.log(user.getId(), ActivityAction.TRANSFERRED, "TRANSFER",
                "Wallet transfer completed: " + transaction.getReference());
        return transactionService.mapToResponse(transaction);
    }

    private WalletBalance lockBalance(Wallet wallet, Currency currency) {
        return balanceRepository.findByWalletIdAndCurrencyForUpdate(wallet.getId(), currency)
                .orElseThrow(() -> new InvalidOperationException("Wallet balance is unavailable for this currency"));
    }

    private void validateWallets(Wallet source, Wallet destination) {
        if (source.getId().equals(destination.getId())) {
            throw new InvalidInputException("You cannot transfer to your own wallet");
        }
        if (source.getStatus() != WalletStatus.ACTIVE || destination.getStatus() != WalletStatus.ACTIVE) {
            throw new InvalidOperationException("One of the wallets cannot receive or send transactions");
        }
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new InvalidInputException("A valid Idempotency-Key header is required");
        }
    }

    private String hashRequest(WalletTransferRequest request) {
        String canonical = "WALLET_TRANSFER|" + request.destinationWalletNumber() + "|" + request.amount().setScale(2).toPlainString()
                + "|" + request.currency() + "|" + cleanNarration(request.narration());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String generateReference(String prefix) {
        return "VPT-" + prefix + "-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private String cleanNarration(String narration) {
        return narration == null || narration.isBlank() ? "Wallet transfer" : narration.trim();
    }
}
