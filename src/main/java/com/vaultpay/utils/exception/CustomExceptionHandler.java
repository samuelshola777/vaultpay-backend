package com.vaultpay.utils.exception;

import com.vaultpay.utils.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
@Slf4j
public class CustomExceptionHandler {

    @ExceptionHandler(EmailVerificationRequiredException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleEmailVerificationRequired(
            EmailVerificationRequiredException exception
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", "EMAIL_NOT_VERIFIED");
        data.put("email", exception.getEmail());
        data.put("emailVerified", false);
        data.put("canResendVerificationOtp", true);
        data.put("resendEndpoint", "/api/v1/users/public/resend-verification");

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(false, exception.getMessage(), data));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(false, exception.getMessage(), null));
    }

    @ExceptionHandler({InvalidInputException.class, InvalidOperationException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(RuntimeException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(false, exception.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(false, "Validation failed", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(false, "Request body is invalid", null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(false, "The request conflicts with existing data", null));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiResponse<Void>> handleProviderFailure(RestClientException exception) {
        log.error("External provider request failed", exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiResponse<>(false, "Payment provider is temporarily unavailable", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unexpected request processing error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(false, "An unexpected error occurred", null));
    }
}
