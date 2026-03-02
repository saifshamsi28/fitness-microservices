package com.saif.fitness.userservice.service;

import com.saif.fitness.userservice.models.OtpState;
import com.saif.fitness.userservice.repository.OtpStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Database-backed OTP service using a SINGLE table (otp_state).
 *
 * Design decisions:
 *  - One row per (email, otp_type) — always upserted, never re-inserted.
 *    Table size = unique emails  2 types. No cleanup job needed.
 *  - OTPs stored as SHA-256 hashes. Plain OTP is only in memory long enough to email.
 *  - generateOtp() holds a PESSIMISTIC_WRITE lock so concurrent requests for
 *    the same email cannot both pass the quota check before either commits.
 *  - Rate-limit quota resets to 0 after successful verification.
 *
 * Why not store on the User table?
 *  - Signup OTP is sent BEFORE the user row exists, so there is nothing to attach it to.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    public enum OtpType { FORGOT_PASSWORD, EMAIL_VERIFICATION }

    private static final long OTP_TTL_SECONDS     = 10 * 60L;
    private static final long TOKEN_TTL_SECONDS   = 15 * 60L;
    private static final int  MAX_VERIFY_ATTEMPTS = 5;
    private static final long COOLDOWN_SECONDS    = 60L;
    static final         int  MAX_SENDS           = 5;
    private static final long RATE_WINDOW_SECONDS = 2 * 60 * 60L;

    private final OtpStateRepository   otpStateRepo;
    private final SecureRandom         random = new SecureRandom();

    /** Reset-token store — in-memory is fine: short-lived and single-use. */
    private final Map<String, ResetEntry> resetTokenStore = new ConcurrentHashMap<>();

    // =========================================================================
    //  PUBLIC API
    // =========================================================================

    /**
     * Generates a 6-digit OTP, persists its hash, and returns send-quota info.
     *
     * @throws CooldownException          if last OTP was sent < 60 s ago
     * @throws RateLimitExceededException if the 2-hour send quota is exhausted
     */
    @Transactional
    public SendOtpResult generateOtp(String email, OtpType type) {
        String  norm    = normalise(email);
        String  typeKey = type.name();
        Instant now     = Instant.now();

        // Load or create the row, held with a write-lock for the duration of the transaction
        OtpState state = otpStateRepo
                .findByEmailAndOtpTypeForUpdate(norm, typeKey)
                .orElseGet(() -> otpStateRepo.save(new OtpState(norm, typeKey)));

        //  Window expiry reset 
        if (state.getWindowStart() == null ||
                now.isAfter(state.getWindowStart().plusSeconds(RATE_WINDOW_SECONDS))) {
            state.setSendCount(0);
            state.setWindowStart(now);
        }

        //  60-second cooldown 
        if (state.getOtpSentAt() != null) {
            long elapsed = now.getEpochSecond() - state.getOtpSentAt().getEpochSecond();
            if (elapsed < COOLDOWN_SECONDS) {
                long wait = COOLDOWN_SECONDS - elapsed;
                throw new CooldownException(wait, state.getSendCount(), MAX_SENDS);
            }
        }

        //  Send-quota check 
        if (state.getSendCount() >= MAX_SENDS) {
            long windowEnds = state.getWindowStart().getEpochSecond() + RATE_WINDOW_SECONDS;
            long retryAfter = Math.max(1L, windowEnds - now.getEpochSecond());
            throw new RateLimitExceededException(retryAfter, state.getSendCount(), MAX_SENDS);
        }

        //  Generate OTP, hash it, store on the row 
        String plainOtp = String.format("%06d", random.nextInt(1_000_000));
        state.setOtpHash(sha256(plainOtp));
        state.setOtpExpiresAt(now.plusSeconds(OTP_TTL_SECONDS));
        state.setOtpSentAt(now);
        state.setVerifyAttempts(0);
        state.setSendCount(state.getSendCount() + 1);
        otpStateRepo.save(state);

        long windowResetIn = (state.getWindowStart().getEpochSecond() + RATE_WINDOW_SECONDS)
                             - now.getEpochSecond();

        log.info("OTP generated for {} [{}]. Send {}/{}", norm, type, state.getSendCount(), MAX_SENDS);
        return new SendOtpResult(plainOtp, state.getSendCount(), MAX_SENDS, windowResetIn);
    }

    /**
     * Verifies an OTP.
     *
     * On success:
     *  - Clears the OTP fields (otpHash, expiresAt, etc.) on the row.
     *  - Resets the send quota so the user gets a fresh 5-request window next time.
     *  - FORGOT_PASSWORD: returns a single-use 15-minute reset token.
     *  - EMAIL_VERIFICATION: returns "OK".
     *
     * @throws OtpNotFoundException         no active OTP for this email+type
     * @throws ExpiredOtpException          OTP has passed its 10-minute TTL
     * @throws InvalidOtpException          wrong code (includes remaining attempts)
     * @throws MaxAttemptsExceededException all verify attempts exhausted
     */
    @Transactional
    public String verifyOtp(String email, String otp, OtpType type) {
        String  norm    = normalise(email);
        String  typeKey = type.name();
        Instant now     = Instant.now();

        OtpState state = otpStateRepo.findByEmailAndOtpType(norm, typeKey)
                .filter(s -> s.getOtpHash() != null)   // row exists but no pending OTP
                .orElseThrow(OtpNotFoundException::new);

        if (now.isAfter(state.getOtpExpiresAt())) {
            state.clearOtp();
            otpStateRepo.save(state);
            throw new ExpiredOtpException();
        }

        if (state.getVerifyAttempts() >= MAX_VERIFY_ATTEMPTS) {
            state.clearOtp();
            otpStateRepo.save(state);
            throw new MaxAttemptsExceededException();
        }

        if (!sha256(otp).equals(state.getOtpHash())) {
            int used      = state.getVerifyAttempts() + 1;
            int remaining = MAX_VERIFY_ATTEMPTS - used;
            state.setVerifyAttempts(used);
            log.warn("Wrong OTP for {} [{}]: {}/{} attempts", norm, type, used, MAX_VERIFY_ATTEMPTS);
            if (remaining <= 0) {
                state.clearOtp();
                otpStateRepo.save(state);
                throw new MaxAttemptsExceededException();
            }
            otpStateRepo.save(state);
            throw new InvalidOtpException(remaining, MAX_VERIFY_ATTEMPTS);
        }

        //  Correct OTP 
        state.clearOtp();    // remove otp_hash, expires_at, sent_at, attempt_count
        state.resetQuota();  // reset send_count and window_start
        otpStateRepo.save(state);
        log.info("OTP verified for {} [{}]. Quota reset.", norm, type);

        if (type == OtpType.FORGOT_PASSWORD) {
            String token = UUID.randomUUID().toString();
            resetTokenStore.put(token,
                    new ResetEntry(norm, Instant.now().plusSeconds(TOKEN_TTL_SECONDS)));
            return token;
        }
        return "OK";
    }

    /** Consumes a single-use reset token. Returns owner email or null if invalid/expired. */
    public String consumeResetToken(String token) {
        ResetEntry e = resetTokenStore.remove(token);
        if (e == null || Instant.now().isAfter(e.expiresAt)) return null;
        return e.email;
    }

    // =========================================================================
    //  RESULT TYPE
    // =========================================================================

    public static final class SendOtpResult {
        public final String otp;
        public final int    sendCount;
        public final int    maxSends;
        public final long   windowResetInSeconds;

        public SendOtpResult(String otp, int sendCount, int maxSends, long windowResetInSeconds) {
            this.otp                  = otp;
            this.sendCount            = sendCount;
            this.maxSends             = maxSends;
            this.windowResetInSeconds = windowResetInSeconds;
        }
    }

    // =========================================================================
    //  EXCEPTIONS
    // =========================================================================

    public static class CooldownException extends RuntimeException {
        private final long retryAfterSeconds;
        private final int  sendCount;
        private final int  maxSends;
        public CooldownException(long retryAfterSeconds, int sendCount, int maxSends) {
            super("Please wait " + retryAfterSeconds + "s before requesting another OTP. ("
                    + sendCount + "/" + maxSends + " OTP requests used)");
            this.retryAfterSeconds = retryAfterSeconds;
            this.sendCount         = sendCount;
            this.maxSends          = maxSends;
        }
        public long getRetryAfterSeconds() { return retryAfterSeconds; }
        public int  getSendCount()         { return sendCount; }
        public int  getMaxSends()          { return maxSends; }
    }

    public static class RateLimitExceededException extends RuntimeException {
        private final long retryAfterSeconds;
        private final int  sendCount;
        private final int  maxSends;
        public RateLimitExceededException(long retryAfterSeconds, int sendCount, int maxSends) {
            super("You've used all " + maxSends + "/" + maxSends
                    + " OTP requests. Try again in " + formatDuration(retryAfterSeconds) + ".");
            this.retryAfterSeconds = retryAfterSeconds;
            this.sendCount         = sendCount;
            this.maxSends          = maxSends;
        }
        public long getRetryAfterSeconds() { return retryAfterSeconds; }
        public int  getSendCount()         { return sendCount; }
        public int  getMaxSends()          { return maxSends; }
        private static String formatDuration(long secs) {
            long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
            if (h > 0) return h + "h " + (m > 0 ? m + "m" : "");
            if (m > 0) return m + "m " + (s > 0 ? s + "s" : "");
            return secs + "s";
        }
    }

    public static class InvalidOtpException extends RuntimeException {
        private final int attemptsRemaining;
        private final int maxAttempts;
        public InvalidOtpException(int attemptsRemaining, int maxAttempts) {
            super("Incorrect OTP. " + attemptsRemaining + "/" + maxAttempts
                    + " attempt" + (attemptsRemaining == 1 ? "" : "s") + " remaining.");
            this.attemptsRemaining = attemptsRemaining;
            this.maxAttempts       = maxAttempts;
        }
        public int getAttemptsRemaining() { return attemptsRemaining; }
        public int getMaxAttempts()       { return maxAttempts; }
    }

    public static class ExpiredOtpException extends RuntimeException {
        public ExpiredOtpException() { super("OTP has expired. Please request a new one."); }
    }

    public static class OtpNotFoundException extends RuntimeException {
        public OtpNotFoundException() { super("No active OTP found. Please request a new one."); }
    }

    public static class MaxAttemptsExceededException extends RuntimeException {
        public MaxAttemptsExceededException() {
            super("Too many incorrect attempts. Please request a new OTP.");
        }
    }

    // =========================================================================
    //  INTERNALS
    // =========================================================================

    private static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static String sha256(String input) {
        try {
            MessageDigest md   = MessageDigest.getInstance("SHA-256");
            byte[]        hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb   = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static final class ResetEntry {
        final String  email;
        final Instant expiresAt;
        ResetEntry(String email, Instant expiresAt) {
            this.email = email; this.expiresAt = expiresAt;
        }
    }
}
