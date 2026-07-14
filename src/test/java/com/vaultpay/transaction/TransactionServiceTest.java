package com.vaultpay.transaction;

import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.wallet.Currency;
import com.vaultpay.wallet.Wallet;
import com.vaultpay.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private WalletTransactionRepository repository;
    @Mock private WalletService walletService;

    @Test
    void recognizesTransactionOwnershipByWalletIdAcrossDifferentEntityInstances() {
        TransactionService service = new TransactionService(repository, walletService);
        User user = User.builder().email("samuel@example.com").build();
        UUID walletId = UUID.randomUUID();
        Wallet currentWallet = Wallet.builder().walletNumber("1234567890").build();
        currentWallet.setId(walletId);
        Wallet attachedWallet = Wallet.builder().walletNumber("1234567890").build();
        attachedWallet.setId(walletId);
        WalletTransaction transaction = WalletTransaction.builder()
                .reference("VPT-TRF-1").type(TransactionType.WALLET_TRANSFER)
                .status(TransactionStatus.COMPLETED).currency(Currency.NGN)
                .amount(new BigDecimal("100.00")).fee(BigDecimal.ZERO)
                .sourceWallet(attachedWallet).build();

        when(walletService.getWalletEntity(user)).thenReturn(currentWallet);
        when(repository.findByReference("VPT-TRF-1")).thenReturn(Optional.of(transaction));

        assertThat(service.getByReference(user, "VPT-TRF-1").reference()).isEqualTo("VPT-TRF-1");
    }
}
