package com.vaultpay.beneficiary;

import tools.jackson.databind.ObjectMapper;
import com.vaultpay.beneficiary.request.BeneficiaryCreateRequest;
import com.vaultpay.payment.PaystackClient;
import com.vaultpay.userauthmgt.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BeneficiaryServiceTest {

    @Mock private BeneficiaryRepository repository;
    @Mock private PaystackClient paystackClient;
    private BeneficiaryService service;

    @BeforeEach
    void setUp() {
        service = new BeneficiaryService(repository, paystackClient);
    }

    @Test
    void reactivatesASoftDeletedBeneficiaryInsteadOfViolatingTheUniqueConstraint() throws Exception {
        User user = User.builder().email("samuel@example.com").build();
        Beneficiary deleted = Beneficiary.builder()
                .user(user).accountNumber("0123456789").bankCode("058")
                .bankName("Old bank name").accountName("Old name")
                .recipientCode("old-code").active(false).build();
        BeneficiaryCreateRequest request = new BeneficiaryCreateRequest("0123456789", "058", "GTBank");

        when(repository.findByUserAndAccountNumberAndBankCode(user, "0123456789", "058"))
                .thenReturn(Optional.of(deleted));
        when(paystackClient.resolveAccount("0123456789", "058"))
                .thenReturn(new ObjectMapper().readTree(
                        "{\"status\":true,\"data\":{\"account_name\":\"Samuel Shola\"}}"));
        when(paystackClient.createTransferRecipient("Samuel Shola", "0123456789", "058"))
                .thenReturn(new ObjectMapper().readTree(
                        "{\"status\":true,\"data\":{\"recipient_code\":\"RCP_new\"}}"));
        when(repository.save(any(Beneficiary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(user, request);

        assertThat(deleted.isActive()).isTrue();
        assertThat(deleted.getRecipientCode()).isEqualTo("RCP_new");
        assertThat(response.accountName()).isEqualTo("Samuel Shola");
    }
}
