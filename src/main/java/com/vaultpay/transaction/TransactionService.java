package com.vaultpay.transaction;

import com.vaultpay.transaction.response.TransactionResponse;
import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.utils.exception.ResourceNotFoundException;
import com.vaultpay.wallet.Wallet;
import com.vaultpay.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final WalletTransactionRepository transactionRepository;
    private final WalletService walletService;

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getMyTransactions(User user, int page, int size) {
        Wallet wallet = walletService.getWalletEntity(user);
        int safePage = Math.max(page - 1, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return transactionRepository.findAllForWallet(
                wallet,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getByReference(User user, String reference) {
        Wallet wallet = walletService.getWalletEntity(user);
        WalletTransaction transaction = transactionRepository.findByReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        boolean ownsTransaction = hasWalletId(transaction.getSourceWallet(), wallet.getId())
                || hasWalletId(transaction.getDestinationWallet(), wallet.getId());
        if (!ownsTransaction) {
            throw new ResourceNotFoundException("Transaction not found");
        }
        return mapToResponse(transaction);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getByReferenceForAdmin(String reference) {
        return transactionRepository.findByReference(reference).map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
    }

    public TransactionResponse mapToResponse(WalletTransaction transaction) {
        return new TransactionResponse(
                transaction.getId(), transaction.getReference(), transaction.getType(), transaction.getStatus(),
                transaction.getCurrency(), transaction.getAmount(), transaction.getFee(),
                transaction.getSourceWallet() == null ? null : transaction.getSourceWallet().getWalletNumber(),
                transaction.getDestinationWallet() == null ? null : transaction.getDestinationWallet().getWalletNumber(),
                transaction.getNarration(), transaction.getCreatedAt()
        );
    }

    private boolean hasWalletId(Wallet candidate, java.util.UUID walletId) {
        return candidate != null && candidate.getId() != null && candidate.getId().equals(walletId);
    }
}
