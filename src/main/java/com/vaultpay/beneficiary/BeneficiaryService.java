package com.vaultpay.beneficiary;

import tools.jackson.databind.JsonNode;
import com.vaultpay.beneficiary.request.BeneficiaryCreateRequest;
import com.vaultpay.beneficiary.response.BeneficiaryResponse;
import com.vaultpay.beneficiary.response.BankResponse;
import com.vaultpay.payment.PaystackClient;
import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.utils.exception.InvalidOperationException;
import com.vaultpay.utils.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final PaystackClient paystackClient;

    @Transactional
    public BeneficiaryResponse create(User user, BeneficiaryCreateRequest request) {
        var existing = beneficiaryRepository.findByUserAndAccountNumberAndBankCode(
                user, request.accountNumber(), request.bankCode());
        if (existing.filter(Beneficiary::isActive).isPresent()) {
            throw new InvalidOperationException("Beneficiary already exists");
        }
        JsonNode resolved = paystackClient.resolveAccount(request.accountNumber(), request.bankCode());
        if (resolved == null || !resolved.path("status").asBoolean(false)) {
            throw new InvalidOperationException("Bank account could not be verified");
        }
        String accountName = resolved.path("data").path("account_name").asText();
        JsonNode recipient = paystackClient.createTransferRecipient(accountName, request.accountNumber(), request.bankCode());
        String recipientCode = recipient.path("data").path("recipient_code").asText();
        if (recipientCode.isBlank()) {
            throw new InvalidOperationException("Transfer recipient could not be created");
        }

        Beneficiary beneficiary = existing.orElseGet(() -> Beneficiary.builder()
                .user(user).accountNumber(request.accountNumber()).bankCode(request.bankCode()).build());
        beneficiary.setAccountName(accountName);
        beneficiary.setBankName(request.bankName().trim());
        beneficiary.setRecipientCode(recipientCode);
        beneficiary.setActive(true);
        return map(beneficiaryRepository.save(beneficiary));
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> getAll(User user) {
        return beneficiaryRepository.findAllByUserAndActiveTrueOrderByCreatedAtDesc(user).stream().map(this::map).toList();
    }

    public List<BankResponse> getBanks() {
        JsonNode data = paystackClient.listNigerianBanks().path("data");
        List<BankResponse> banks = new ArrayList<>();
        if (data.isArray()) {
            data.forEach(bank -> banks.add(new BankResponse(bank.path("name").asText(), bank.path("code").asText())));
        }
        return banks.stream().filter(bank -> !bank.name().isBlank() && !bank.code().isBlank()).toList();
    }

    @Transactional
    public void delete(User user, UUID id) {
        Beneficiary beneficiary = getEntity(user, id);
        beneficiary.setActive(false);
        beneficiaryRepository.save(beneficiary);
    }

    public Beneficiary getEntity(User user, UUID id) {
        return beneficiaryRepository.findByIdAndUserAndActiveTrue(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary not found"));
    }

    private BeneficiaryResponse map(Beneficiary value) {
        return new BeneficiaryResponse(value.getId(), value.getAccountName(), value.getAccountNumber(),
                value.getBankName(), value.getBankCode());
    }
}
