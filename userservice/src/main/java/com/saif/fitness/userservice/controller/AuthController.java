package com.saif.fitness.userservice.controller;

import com.saif.fitness.userservice.dto.SignupRequest;
import com.saif.fitness.userservice.dto.SignupResponse;
import com.saif.fitness.userservice.service.EmailVerificationService;
import com.saif.fitness.userservice.service.KeycloakAdminService;
import com.saif.fitness.userservice.service.OtpService;
import com.saif.fitness.userservice.service.PasswordResetService;
import com.saif.fitness.userservice.service.SignupService;
import com.saif.fitness.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final SignupService              signupService;
    private final PasswordResetService       passwordResetService;
    private final EmailVerificationService   emailVerificationService;
    private final UserService                userService;

    @PostMapping("/signup")
    public Mono<ResponseEntity<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        log.info("Signup request received for email: {}", request.getEmail());

        return signupService.register(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .onErrorResume(e -> {
                    log.error("Signup failed for '{}': {}", request.getEmail(), e.getMessage());

                    HttpStatus status;
                    String msg;

                    if (e instanceof KeycloakAdminService.UserAlreadyExistsException) {
                        status = HttpStatus.CONFLICT;
                        msg    = "Email already registered. Please login.";
                    } else if (e instanceof UserService.UsernameAlreadyTakenException) {
                        status = HttpStatus.CONFLICT;
                        msg    = e.getMessage();
                    } else if (e instanceof KeycloakAdminService.KeycloakUserCreationException) {
                        status = HttpStatus.INTERNAL_SERVER_ERROR;
                        msg    = "Failed to create user account. Please try again.";
                    } else {
                        status = HttpStatus.BAD_REQUEST;
                        msg    = "Signup failed. Please try again.";
                    }

                    return Mono.just(ResponseEntity.status(status)
                            .body(SignupResponse.builder().success(false).message(msg).build()));
                });
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<String>> health() {
        return Mono.just(ResponseEntity.ok("Auth service is running"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FORGOT PASSWORD — OTP flow
    // Step 1: send OTP  →  Step 2: verify OTP (get reset token)  →  Step 3: reset
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Send a password-reset OTP to the given email.
     * Always returns 200 so callers cannot enumerate registered emails.
     */
    @PostMapping("/forgot-password/send-otp")
    public Mono<ResponseEntity<Map<String, Object>>> forgotPasswordSendOtp(
            @RequestBody Map<String, String> body) {

        String email = body != null ? body.get("email") : null;
        if (email == null || email.isBlank())
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Email is required")));

        return Mono.fromCallable(() -> passwordResetService.sendOtp(email))
                .map(result -> ResponseEntity.ok(Map.<String, Object>of(
                        "success", true,
                        "message", "If that email is registered, an OTP has been sent.",
                        "sendCount", result.sendCount,
                        "maxSends",  result.maxSends,
                        "windowResetInSeconds", result.windowResetInSeconds)))
                .onErrorResume(OtpService.CooldownException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                .<Map<String, Object>>body(Map.of(
                                        "success", false,
                                        "message", e.getMessage(),
                                        "retryAfterSeconds", e.getRetryAfterSeconds(),
                                        "sendCount", e.getSendCount(),
                                        "maxSends",  e.getMaxSends()))))
                .onErrorResume(OtpService.RateLimitExceededException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                .<Map<String, Object>>body(Map.of(
                                        "success", false,
                                        "message", e.getMessage(),
                                        "retryAfterSeconds", e.getRetryAfterSeconds(),
                                        "sendCount", e.getSendCount(),
                                        "maxSends",  e.getMaxSends()))))
                .onErrorResume(e -> {
                    log.error("forgot-password/send-otp error: {}", e.getMessage());
                    // still 200 — do not leak existence
                    return Mono.just(ResponseEntity.ok(Map.<String, Object>of(
                            "success", true,
                            "message", "If that email is registered, an OTP has been sent.")));
                });
    }

    /**
     * Verify the OTP and return a single-use reset token on success.
     */
    @PostMapping("/forgot-password/verify-otp")
    public Mono<ResponseEntity<Map<String, Object>>> forgotPasswordVerifyOtp(
            @RequestBody Map<String, String> body) {

        String email = body != null ? body.get("email") : null;
        String otp   = body != null ? body.get("otp")   : null;
        if (email == null || otp == null)
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Email and OTP are required")));

        return Mono.fromCallable(() -> passwordResetService.verifyOtp(email, otp))
                .map(token -> ResponseEntity.ok(
                        Map.<String, Object>of("success", true, "resetToken", token)))
                .onErrorResume(OtpService.InvalidOtpException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                .<Map<String, Object>>body(Map.of(
                                        "success", false,
                                        "message", e.getMessage(),
                                        "attemptsRemaining", e.getAttemptsRemaining(),
                                        "maxAttempts", e.getMaxAttempts()))))
                .onErrorResume(OtpService.MaxAttemptsExceededException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                .<Map<String, Object>>body(Map.of(
                                        "success", false,
                                        "message", e.getMessage(),
                                        "attemptsRemaining", 0))))
                .onErrorResume(OtpService.ExpiredOtpException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                .<Map<String, Object>>body(Map.of(
                                        "success", false, "message", e.getMessage()))))
                .onErrorResume(OtpService.OtpNotFoundException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                .<Map<String, Object>>body(Map.of(
                                        "success", false, "message", e.getMessage()))));
    }

    /**
     * Consume the reset token and set the new password in Keycloak.
     */
    @PostMapping("/forgot-password/reset")
    public Mono<ResponseEntity<Map<String, Object>>> forgotPasswordReset(
            @RequestBody Map<String, String> body) {

        String resetToken  = body != null ? body.get("resetToken")  : null;
        String newPassword = body != null ? body.get("newPassword") : null;
        if (resetToken == null || newPassword == null || newPassword.length() < 8)
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message",
                            "Reset token and new password (min 8 chars) are required")));

        return Mono.fromRunnable(() -> passwordResetService.resetPassword(resetToken, newPassword))
                .thenReturn(ResponseEntity.ok(
                        Map.<String, Object>of("success", true, "message", "Password reset successfully")))
                .onErrorResume(e -> {
                    log.error("forgot-password/reset error: {}", e.getMessage());
                    String msg = (e.getMessage() != null && e.getMessage().toLowerCase().contains("invalid"))
                            ? e.getMessage() : "Failed to reset password. Please try again.";
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("success", false, "message", msg)));
                });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SIGNUP EMAIL VERIFICATION — OTP flow
    // Step 1: send OTP to the email  →  Step 2: verify OTP, then call /signup
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Send a 6-digit email-verification OTP before account creation.
     */
    @PostMapping("/signup/send-otp")
    public Mono<ResponseEntity<Map<String, Object>>> signupSendOtp(
            @RequestBody Map<String, String> body) {

        String email     = body != null ? body.get("email")     : null;
        String firstName = body != null ? body.get("firstName") : null;
        String username  = body != null ? body.get("username")  : null;
        if (email == null || email.isBlank())
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Email is required")));

        final String normalEmail = email.trim().toLowerCase();
        final String name = (firstName != null && !firstName.isBlank()) ? firstName : "there";
        final String normalUsername = (username != null && !username.isBlank()) ? username.trim().toLowerCase() : null;

        return Mono.fromCallable(() -> {
                    userService.checkSignupAvailability(normalEmail, normalUsername);
                    return true;
                })
                .flatMap(ignored -> Mono.fromCallable(() -> emailVerificationService.sendOtp(normalEmail, name)))
                .map(result -> ResponseEntity.ok(Map.<String, Object>of(
                        "success", true,
                        "message", "OTP sent to " + email,
                        "sendCount", result.sendCount,
                        "maxSends",  result.maxSends,
                        "windowResetInSeconds", result.windowResetInSeconds)))
                .onErrorResume(UserService.EmailAlreadyTakenException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                .<Map<String, Object>>body(Map.of(
                                        "success", false, "message", e.getMessage()))))
                .onErrorResume(UserService.UsernameAlreadyTakenException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                .<Map<String, Object>>body(Map.of(
                                        "success", false, "message", e.getMessage()))))
                .onErrorResume(OtpService.CooldownException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                .<Map<String, Object>>body(Map.of(
                                        "success", false,
                                        "message", e.getMessage(),
                                        "retryAfterSeconds", e.getRetryAfterSeconds(),
                                        "sendCount", e.getSendCount(),
                                        "maxSends",  e.getMaxSends()))))
                .onErrorResume(OtpService.RateLimitExceededException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                .<Map<String, Object>>body(Map.of(
                                        "success", false,
                                        "message", e.getMessage(),
                                        "retryAfterSeconds", e.getRetryAfterSeconds(),
                                        "sendCount", e.getSendCount(),
                                        "maxSends",  e.getMaxSends()))))
                .onErrorResume(e -> {
                    log.error("signup/send-otp error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("success", false, "message", "Failed to send OTP. Please try again.")));
                });
    }

    /**
     * Verify the signup OTP.  On success the client may proceed to call POST /signup.
     */
    @PostMapping("/signup/verify-otp")
    public Mono<ResponseEntity<Map<String, Object>>> signupVerifyOtp(
            @RequestBody Map<String, String> body) {

        String email = body != null ? body.get("email") : null;
        String otp   = body != null ? body.get("otp")   : null;
        if (email == null || otp == null)
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Email and OTP are required")));

        return Mono.fromCallable(() -> emailVerificationService.verifyOtp(email, otp))
                .map(verified -> ResponseEntity.ok(
                        Map.<String, Object>of("success", true, "message", "Email verified")))
                .onErrorResume(OtpService.InvalidOtpException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                .<Map<String, Object>>body(Map.of(
                                        "success", false,
                                        "message", e.getMessage(),
                                        "attemptsRemaining", e.getAttemptsRemaining(),
                                        "maxAttempts", e.getMaxAttempts()))))
                .onErrorResume(OtpService.MaxAttemptsExceededException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                .<Map<String, Object>>body(Map.of(
                                        "success", false,
                                        "message", e.getMessage(),
                                        "attemptsRemaining", 0))))
                .onErrorResume(OtpService.ExpiredOtpException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                .<Map<String, Object>>body(Map.of(
                                        "success", false, "message", e.getMessage()))))
                .onErrorResume(OtpService.OtpNotFoundException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                .<Map<String, Object>>body(Map.of(
                                        "success", false, "message", e.getMessage()))));
    }
}