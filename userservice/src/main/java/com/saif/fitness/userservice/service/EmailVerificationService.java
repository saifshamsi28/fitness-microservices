package com.saif.fitness.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Handles the OTP-based signup email-verification flow:
 *   1. sendOtp(email, firstName) — generate OTP, email it
 *   2. verifyOtp(email, otp)     — validate OTP; returns "OK" on success or null on failure
 *
 * The actual account creation (Keycloak + DB) still goes through the existing /signup endpoint,
 * but only proceeds after the client has a verified OTP response.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final OtpService   otpService;
    private final EmailService emailService;

    /**
     * Generate and send a 6-digit verification OTP to the given email.
     *
     * @return {@link OtpService.SendOtpResult} with sendCount / maxSends for the UI
     * @throws OtpService.CooldownException          if last OTP was sent < 60 s ago
     * @throws OtpService.RateLimitExceededException if the 2-hour quota is exhausted
     */
    public OtpService.SendOtpResult sendOtp(String email, String firstName) {
        String normalised = email.trim().toLowerCase();
        String name       = (firstName != null && !firstName.isBlank()) ? firstName.trim() : "there";
        OtpService.SendOtpResult result =
                otpService.generateOtp(normalised, OtpService.OtpType.EMAIL_VERIFICATION);
        emailService.sendSignupVerificationOtp(email.trim(), name, result.otp);
        log.info("Signup OTP dispatched to '{}' ({}/{})",
                normalised, result.sendCount, result.maxSends);
        return result;
    }

    /**
     * Verify the OTP for signup email verification.
     * @return {@code true} if valid and not expired, {@code false} otherwise
     */
    public boolean verifyOtp(String email, String otp) {
        String result = otpService.verifyOtp(
                email.trim().toLowerCase(), otp.trim(), OtpService.OtpType.EMAIL_VERIFICATION);
        return result != null;
    }
}
