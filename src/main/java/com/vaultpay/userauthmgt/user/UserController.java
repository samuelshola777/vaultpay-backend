package com.vaultpay.userauthmgt.user;

import com.vaultpay.userauthmgt.user.request.UserCreateRequest;
import com.vaultpay.userauthmgt.user.request.UserLoginRequest;
import com.vaultpay.userauthmgt.user.request.UserForgotPasswordRequest;
import com.vaultpay.userauthmgt.user.request.UserResetPasswordRequest;
import com.vaultpay.userauthmgt.user.request.UserResendOtpRequest;
import com.vaultpay.userauthmgt.user.request.UserVerifyOtpRequest;
import com.vaultpay.userauthmgt.user.request.UserRefreshTokenRequest;
import com.vaultpay.userauthmgt.user.response.UserAuthResponse;
import com.vaultpay.userauthmgt.user.response.UserResponse;
import com.vaultpay.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/public/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                true,
                "Registration successful. Check your email for the verification code",
                userService.register(request)
        ));
    }

    @PostMapping("/public/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody UserVerifyOtpRequest request) {
        userService.verifyEmail(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Email verified successfully", null));
    }

    @PostMapping("/public/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody UserResendOtpRequest request
    ) {
        userService.resendVerificationOtp(request.email());
        return ResponseEntity.ok(new ApiResponse<>(true, "Verification code sent", null));
    }

    @PostMapping("/public/login")
    public ResponseEntity<ApiResponse<UserAuthResponse>> login(@Valid @RequestBody UserLoginRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(
                true,
                "Login successful",
                userService.login(request)
        ));
    }

    @PostMapping("/public/refresh-token")
    public ResponseEntity<ApiResponse<UserAuthResponse>> refreshToken(
            @Valid @RequestBody UserRefreshTokenRequest request
    ) {
        return ResponseEntity.ok(new ApiResponse<>(
                true, "Token refreshed successfully", userService.refresh(request.refreshToken())
        ));
    }

    @PostMapping("/private/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal User user) {
        userService.logout(user);
        return ResponseEntity.ok(new ApiResponse<>(true, "Logout successful", null));
    }

    @PostMapping("/public/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody UserForgotPasswordRequest request
    ) {
        userService.forgotPassword(request.email());
        return ResponseEntity.ok(new ApiResponse<>(
                true, "If the account exists, a password reset code has been sent", null
        ));
    }

    @PostMapping("/public/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody UserResetPasswordRequest request
    ) {
        userService.resetPassword(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Password reset successfully", null));
    }

    @GetMapping("/private/profile")
    public ResponseEntity<ApiResponse<UserResponse>> profile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Profile fetched successfully", userService.getProfile(user)));
    }
}
