package com.vaultpay.userauthmgt.user;

import com.vaultpay.activitylog.ActivityLogService;
import com.vaultpay.notification.NotificationService;
import com.vaultpay.userauthmgt.user.request.UserLoginRequest;
import com.vaultpay.userauthmgt.user.request.UserCreateRequest;
import com.vaultpay.userauthmgt.user.request.UserResetPasswordRequest;
import com.vaultpay.userauthmgt.userotp.UserOtp;
import com.vaultpay.userauthmgt.userotp.UserOtpRepository;
import com.vaultpay.userauthmgt.userotp.UserOtpType;
import com.vaultpay.utils.appsecurity.JwtService;
import com.vaultpay.utils.emailsenderservice.EmailSenderService;
import com.vaultpay.utils.exception.InvalidInputException;
import com.vaultpay.utils.exception.EmailVerificationRequiredException;
import com.vaultpay.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserOtpRepository userOtpRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private EmailSenderService emailSenderService;
    @Mock private WalletService walletService;
    @Mock private NotificationService notificationService;
    @Mock private ActivityLogService activityLogService;
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, userOtpRepository, passwordEncoder, jwtService,
                emailSenderService, walletService, notificationService, activityLogService);
        ReflectionTestUtils.setField(service, "maxLoginAttempts", 5);
        ReflectionTestUtils.setField(service, "loginLockMinutes", 15L);
        ReflectionTestUtils.setField(service, "otpExpirationMinutes", 10L);
    }

    @Test
    void locksAccountAfterConfiguredFailedLogins() {
        User user = User.builder().email("samuel@example.com").password("hash").build();
        when(userRepository.findByEmailIgnoreCase("samuel@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hash")).thenReturn(false);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.login(new UserLoginRequest("samuel@example.com", "wrong-password")))
                    .isInstanceOf(InvalidInputException.class);
        }

        assertThat(user.getLockedUntil()).isNotNull();
    }

    @Test
    void registrationSucceedsWhenVerificationEmailDeliveryFails() {
        when(userRepository.existsByEmailIgnoreCase("samuel@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any(String.class))).thenReturn("otp-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(userOtpRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("SMTP unavailable")).when(emailSenderService)
                .sendVerificationOtp(any(), any(), any());

        var response = service.register(new UserCreateRequest(
                "Samuel", "Shola", "samuel@example.com", "StrongPassword1!", "+2348012345678"));

        assertThat(response.email()).isEqualTo("samuel@example.com");
        assertThat(response.emailVerified()).isFalse();
    }

    @Test
    void loginReturnsVerificationRequiredForCorrectPasswordAndUnverifiedEmail() {
        User user = User.builder()
                .email("samuel@example.com").password("hash")
                .emailVerified(false).status(UserStatus.PENDING_VERIFICATION).build();
        when(userRepository.findByEmailIgnoreCase("samuel@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("StrongPassword1!", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(
                new UserLoginRequest("samuel@example.com", "StrongPassword1!")))
                .isInstanceOf(EmailVerificationRequiredException.class)
                .hasMessage("Email verification is required");
    }

    @Test
    void forgotPasswordStoresHashedOtpAndEmailsTheUser() {
        User user = User.builder().firstName("Samuel").email("samuel@example.com").build();
        when(userRepository.findByEmailIgnoreCase("samuel@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("otp-hash");

        LocalDateTime before = LocalDateTime.now();
        service.forgotPassword("  SAMUEL@example.com ");

        ArgumentCaptor<UserOtp> storedOtp = ArgumentCaptor.forClass(UserOtp.class);
        ArgumentCaptor<String> emailedCode = ArgumentCaptor.forClass(String.class);
        verify(userOtpRepository).deleteAllByUserAndType(user, UserOtpType.PASSWORD_RESET);
        verify(userOtpRepository).save(storedOtp.capture());
        verify(emailSenderService).sendPasswordResetOtp(eq("samuel@example.com"), eq("Samuel"),
                emailedCode.capture());

        assertThat(emailedCode.getValue()).matches("^\\d{6}$");
        assertThat(storedOtp.getValue().getCodeHash()).isEqualTo("otp-hash");
        assertThat(storedOtp.getValue().getType()).isEqualTo(UserOtpType.PASSWORD_RESET);
        assertThat(storedOtp.getValue().isUsed()).isFalse();
        assertThat(storedOtp.getValue().getExpiresAt())
                .isAfterOrEqualTo(before.plusMinutes(10))
                .isBeforeOrEqualTo(LocalDateTime.now().plusMinutes(10));
    }

    @Test
    void forgotPasswordDoesNotRevealOrProcessAnUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        service.forgotPassword("missing@example.com");

        verifyNoInteractions(userOtpRepository, emailSenderService);
    }

    @Test
    void resetPasswordConsumesOtpChangesPasswordAndInvalidatesTokens() {
        User user = User.builder().email("samuel@example.com").password("old-hash")
                .failedLoginAttempts(3).tokenVersion(4).lockedUntil(LocalDateTime.now().plusMinutes(10)).build();
        UserOtp otp = UserOtp.builder().user(user).codeHash("otp-hash").type(UserOtpType.PASSWORD_RESET)
                .expiresAt(LocalDateTime.now().plusMinutes(5)).used(false).build();
        when(userRepository.findByEmailIgnoreCase("samuel@example.com")).thenReturn(Optional.of(user));
        when(userOtpRepository.findFirstByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(
                user, UserOtpType.PASSWORD_RESET)).thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("123456", "otp-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewStrong1!")).thenReturn("new-hash");

        service.resetPassword(new UserResetPasswordRequest(
                "samuel@example.com", "123456", "NewStrong1!", "NewStrong1!"));

        assertThat(otp.isUsed()).isTrue();
        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(user.getTokenVersion()).isEqualTo(5);
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(userOtpRepository).save(otp);
        verify(userRepository).save(user);
    }

    @Test
    void resetPasswordRejectsExpiredOtpWithoutChangingPassword() {
        User user = User.builder().email("samuel@example.com").password("old-hash").build();
        UserOtp otp = UserOtp.builder().user(user).codeHash("otp-hash").type(UserOtpType.PASSWORD_RESET)
                .expiresAt(LocalDateTime.now().minusSeconds(1)).used(false).build();
        when(userRepository.findByEmailIgnoreCase("samuel@example.com")).thenReturn(Optional.of(user));
        when(userOtpRepository.findFirstByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(
                user, UserOtpType.PASSWORD_RESET)).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> service.resetPassword(new UserResetPasswordRequest(
                "samuel@example.com", "123456", "NewStrong1!", "NewStrong1!")))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Password reset code is invalid or expired");

        assertThat(user.getPassword()).isEqualTo("old-hash");
        assertThat(otp.isUsed()).isFalse();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resetPasswordUsesGenericErrorForUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(new UserResetPasswordRequest(
                "missing@example.com", "123456", "NewStrong1!", "NewStrong1!")))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Password reset code is invalid or expired");
    }
}
