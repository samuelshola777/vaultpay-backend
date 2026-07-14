package com.vaultpay.beneficiary;

import com.vaultpay.beneficiary.request.BeneficiaryCreateRequest;
import com.vaultpay.beneficiary.response.BeneficiaryResponse;
import com.vaultpay.beneficiary.response.BankResponse;
import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/beneficiaries")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    @PostMapping("/private")
    public ResponseEntity<ApiResponse<BeneficiaryResponse>> create(
            @AuthenticationPrincipal User user, @Valid @RequestBody BeneficiaryCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                true, "Beneficiary created successfully", beneficiaryService.create(user, request)
        ));
    }

    @GetMapping("/private")
    public ResponseEntity<ApiResponse<List<BeneficiaryResponse>>> getAll(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Beneficiaries fetched successfully", beneficiaryService.getAll(user)));
    }

    @GetMapping("/private/banks")
    public ResponseEntity<ApiResponse<List<BankResponse>>> getBanks() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Banks fetched successfully", beneficiaryService.getBanks()));
    }

    @DeleteMapping("/private/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        beneficiaryService.delete(user, id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Beneficiary deleted successfully", null));
    }
}
