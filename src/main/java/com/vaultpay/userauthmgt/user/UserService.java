package com.vaultpay.userauthmgt.user;

import com.vaultpay.userauthmgt.user.request.UserCreateRequest;
import com.vaultpay.userauthmgt.user.request.UserLoginRequest;
import com.vaultpay.userauthmgt.user.request.UserResetPasswordRequest;
import com.vaultpay.userauthmgt.user.request.UserVerifyOtpRequest;
import com.vaultpay.userauthmgt.user.response.UserAuthResponse;
import com.vaultpay.userauthmgt.user.response.UserResponse;
import com.vaultpay.userauthmgt.userotp.UserOtp;
import com.vaultpay.userauthmgt.userotp.UserOtpRepository;
import com.vaultpay.userauthmgt.userotp.UserOtpType;
import com.vaultpay.utils.appsecurity.JwtService;
import com.vaultpay.utils.emailsenderservice.EmailSenderService;
import com.vaultpay.utils.exception.InvalidInputException;
import com.vaultpay.utils.exception.InvalidOperationException;
import com.vaultpay.utils.exception.ResourceNotFoundException;
import com.vaultpay.utils.exception.EmailVerificationRequiredException;
import com.vaultpay.wallet.WalletService;
import com.vaultpay.activitylog.ActivityAction;
import com.vaultpay.activitylog.ActivityLogService;
import com.vaultpay.notification.NotificationService;
import com.vaultpay.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import io.jsonwebtoken.JwtException;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserOtpRepository userOtpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailSenderService emailSenderService;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;

    @Value("${app.security.max-login-attempts:5}")
    private int maxLoginAttempts;

    @Value("${app.security.login-lock-minutes:15}")
    private long loginLockMinutes;

    @Value("${app.otp.expiration-minutes}")
    private long otpExpirationMinutes;

    @Transactional
    public UserResponse register(UserCreateRequest request) {
        try {
            String email = normalizeEmail(request.email());
            if (userRepository.existsByEmailIgnoreCase(email)) {
                throw new InvalidOperationException("An account already exists with this email");
            }

            User user = User.builder()
                    .firstName(clean(request.firstName()))
                    .lastName(clean(request.lastName()))
                    .email(email)
                    .password(passwordEncoder.encode(request.password()))
                    .phoneNumber(cleanNullable(request.phoneNumber()))
                    .role(UserRole.USER)
                    .status(UserStatus.PENDING_VERIFICATION)
                    .emailVerified(false)
                    .build();

            User savedUser = userRepository.save(user);
            String verificationCode = createVerificationOtp(savedUser);
            sendVerificationEmailSafely(savedUser, verificationCode);
            activityLogService.log(savedUser.getId(), ActivityAction.REGISTERED, "USER", "User registered");
            return mapToResponse(savedUser);
        }catch(Exception e){
            log.info(e.getMessage()+"<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
        return mapToResponse(User.builder()
        .firstName("")
        .lastName("")
        .email("")
        .phoneNumber("")
        .role(UserRole.USER)
        .status(UserStatus.PENDING_VERIFICATION)
                .emailVerified(false)
        .build());
        }
    }

    @Transactional
    public void verifyEmail(UserVerifyOtpRequest request) {
        User user = findByEmail(request.email());
        if (user.isEmailVerified()) {
            throw new InvalidOperationException("Email is already verified");
        }

        UserOtp otp = userOtpRepository
                .findFirstByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(user, UserOtpType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new InvalidInputException("Verification code is invalid or expired"));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())
                || !passwordEncoder.matches(request.otp(), otp.getCodeHash())) {
            throw new InvalidInputException("Verification code is invalid or expired");
        }

        otp.setUsed(true);
        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        userOtpRepository.save(otp);
        userRepository.save(user);
        walletService.createWalletForUser(user);
        notificationService.create(user, NotificationType.ACCOUNT, "Account verified",
                "Your VaultPay account and NGN wallet are now active.");
        activityLogService.log(user.getId(), ActivityAction.VERIFIED, "USER", "Email verified");
    }

    @Transactional
    public void resendVerificationOtp(String email) {
        User user = findByEmail(email);
        if (user.isEmailVerified()) {
            throw new InvalidOperationException("Email is already verified");
        }
        String verificationCode = createVerificationOtp(user);
        emailSenderService.sendVerificationOtp(user.getEmail(), user.getFirstName(), verificationCode);
    }

    public UserAuthResponse login(UserLoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email())).orElse(null);
        if (user == null) {
            throw new InvalidInputException("Email or password is incorrect");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new InvalidOperationException("Account is temporarily locked. Try again later");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            registerFailedLogin(user);
            throw new InvalidInputException("Email or password is incorrect");
        }
        if (!user.isEmailVerified()) {
            throw new EmailVerificationRequiredException(user.getEmail());
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidOperationException("Account is not active");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        UserAuthResponse response = tokensFor(user);
        activityLogService.log(user.getId(), ActivityAction.LOGIN, "AUTHENTICATION", "User logged in");
        return response;
    }

    @Transactional
    public UserAuthResponse refresh(String refreshToken) {
        try {
            String email = jwtService.extractSubject(refreshToken);
            User user = userRepository.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new InvalidInputException("Refresh token is invalid"));
            if (user.getStatus() != UserStatus.ACTIVE || !jwtService.isValid(refreshToken, user, "refresh")) {
                throw new InvalidInputException("Refresh token is invalid or expired");
            }
            return tokensFor(user);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidInputException("Refresh token is invalid or expired");
        }
    }

    @Transactional
    public void logout(User user) {
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        activityLogService.log(user.getId(), ActivityAction.LOGOUT, "AUTHENTICATION", "User logged out");
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email)).orElse(null);
        if (user == null) {
            return;
        }
        userOtpRepository.deleteAllByUserAndType(user, UserOtpType.PASSWORD_RESET);
        String code = generateOtp();
        userOtpRepository.save(UserOtp.builder()
                .user(user).codeHash(passwordEncoder.encode(code)).type(UserOtpType.PASSWORD_RESET)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpirationMinutes)).used(false).build());
        emailSenderService.sendPasswordResetOtp(user.getEmail(), user.getFirstName(), code);
    }

    @Transactional
    public void resetPassword(UserResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new InvalidInputException("Password confirmation does not match");
        }
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new InvalidInputException("Password reset code is invalid or expired"));
        UserOtp otp = userOtpRepository
                .findFirstByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(user, UserOtpType.PASSWORD_RESET)
                .orElseThrow(() -> new InvalidInputException("Password reset code is invalid or expired"));
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())
                || !passwordEncoder.matches(request.otp(), otp.getCodeHash())) {
            throw new InvalidInputException("Password reset code is invalid or expired");
        }
        otp.setUsed(true);
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userOtpRepository.save(otp);
        userRepository.save(user);
        notificationService.create(user, NotificationType.SECURITY, "Password changed",
                "Your account password was reset successfully.");
        activityLogService.log(user.getId(), ActivityAction.PASSWORD_RESET, "AUTHENTICATION", "Password reset");
    }

    public UserResponse getProfile(User currentUser) {
        return mapToResponse(currentUser);
    }

    private String createVerificationOtp(User user) {
        userOtpRepository.deleteAllByUserAndType(user, UserOtpType.EMAIL_VERIFICATION);
        String code = generateOtp();
        UserOtp otp = UserOtp.builder()
                .user(user)
                .codeHash(passwordEncoder.encode(code))
                .type(UserOtpType.EMAIL_VERIFICATION)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpirationMinutes))
                .used(false)
                .build();
        userOtpRepository.save(otp);
        return code;
    }

    private void sendVerificationEmailSafely(User user, String code) {
        try {
            emailSenderService.sendVerificationOtp(user.getEmail(), user.getFirstName(), code);
        } catch (RuntimeException exception) {
            log.error("User {} was registered, but the verification email could not be sent",
                    user.getId(), exception);
        }
    }

    private String generateOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private User findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private UserResponse mapToResponse(User user) {
        return new UserResponse(
                user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                user.getPhoneNumber(), user.getRole(), user.getStatus(), user.isEmailVerified(),
                user.getCreatedAt()
        );
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidInputException("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void registerFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= maxLoginAttempts) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(loginLockMinutes));
            user.setFailedLoginAttempts(0);
        }
        userRepository.save(user);
    }

    private UserAuthResponse tokensFor(User user) {
        return new UserAuthResponse(
                jwtService.generateAccessToken(user), jwtService.generateRefreshToken(user),
                "Bearer", jwtService.getExpirationSeconds(), mapToResponse(user)
        );
    }
}
