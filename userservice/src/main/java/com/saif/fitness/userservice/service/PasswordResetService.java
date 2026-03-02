package com.saif.fitness.userservice.service;

import com.saif.fitness.userservice.models.User;
import com.saif.fitness.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Handles the OTP-based forgot-password flow:
 *   1. sendOtp(email)       — generate OTP, email it
 *   2. verifyOtp(email,otp) — validate OTP, return a short-lived reset token
 *   3. resetPassword(token, newPassword) — consume token, set new password in Keycloak
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final OtpService            otpService;
    private final EmailService          emailService;
    private final KeycloakAdminService  keycloakAdminService;
    private final UserRepository        userRepository;

    /**
     * Generate a 6-digit OTP and email it to the user.
     * Always returns silently even if the email is not registered (prevents enumeration).
     *
     * @return {@link OtpService.SendOtpResult} with sendCount / maxSends for the UI
     * @throws OtpService.CooldownException          if last OTP was sent < 60 s ago
     * @throws OtpService.RateLimitExceededException if the 2-hour quota is exhausted
     */
    public OtpService.SendOtpResult sendOtp(String email) {
        String normalised = email.trim().toLowerCase();
        User   user       = userRepository.findByEmail(normalised);
        // generateOtp throws CooldownException / RateLimitExceededException if limited
        OtpService.SendOtpResult result =
                otpService.generateOtp(normalised, OtpService.OtpType.FORGOT_PASSWORD);
        String firstName = (user != null) ? user.getFirstName() : "there";
        emailService.sendPasswordResetOtp(email.trim(), firstName, result.otp);
        log.info("Password-reset OTP dispatched for '{}' ({}/{})",
                normalised, result.sendCount, result.maxSends);
        return result;
    }

    /**
     * Verify the OTP. On success returns a short-lived reset token.
     * Returns {@code null} if the OTP is wrong or expired.
     */
    public String verifyOtp(String email, String otp) {
        return otpService.verifyOtp(
                email.trim().toLowerCase(), otp.trim(), OtpService.OtpType.FORGOT_PASSWORD);
    }

    /**
     * Consume the reset token and set the new password in Keycloak.
     *
     * @throws IllegalArgumentException if the token is invalid/expired
     * @throws RuntimeException         if Keycloak update fails
     */
    public void resetPassword(String resetToken, String newPassword) {
        String email = otpService.consumeResetToken(resetToken);
        if (email == null) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }
        keycloakAdminService.setNewPassword(email, newPassword);
        log.info("Password updated via OTP flow for '{}'", email);
    }
}
