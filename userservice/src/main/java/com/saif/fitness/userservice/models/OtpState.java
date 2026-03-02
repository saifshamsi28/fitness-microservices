package com.saif.fitness.userservice.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Single-table OTP store.
 *
 * One row per (email, otp_type) — always upserted, never inserted fresh.
 * This means the table size is bounded to (unique emails × 2 types) and
 * never needs a cleanup job.
 *
 * Combines what was previously two tables (otp_records + otp_rate_limits):
 *
 *  ── OTP fields (null when no active OTP) ──────────────────────────
 *  otp_hash       SHA-256 hex of the 6-digit code
 *  otp_expires_at when the active OTP expires (10 min TTL)
 *  otp_sent_at    when the current OTP was generated (for 60 s cooldown)
 *  verify_attempts how many wrong guesses on the active OTP
 *
 *  ── Rate-limit fields ─────────────────────────────────────────────
 *  send_count     OTPs sent inside the current 2-hour window
 *  window_start   when the current 2-hour window began
 */
@Entity
@Table(
    name = "otp_state",
    uniqueConstraints = @UniqueConstraint(
        name        = "uk_otp_state_email_type",
        columnNames = {"email", "otp_type"}
    )
)
@Getter @Setter @NoArgsConstructor
public class OtpState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "otp_type", nullable = false, length = 30)
    private String otpType;

    // ── Active OTP (all nullable — null = no pending OTP) ─────────────────────

    /** SHA-256 hex of the 6-digit OTP. Never store the plain code. */
    private String  otpHash;

    /** When the active OTP expires. */
    private Instant otpExpiresAt;

    /** When the active OTP was generated (used for the 60-second cooldown). */
    private Instant otpSentAt;

    /** How many wrong verify attempts have been made on the current OTP. */
    @Column(nullable = false)
    private int verifyAttempts = 0;

    // ── Rate-limit (send quota) ────────────────────────────────────────────────

    /** How many OTPs have been sent within the current 2-hour window. */
    @Column(nullable = false)
    private int sendCount = 0;

    /** When the current 2-hour window started. Null = no window open yet. */
    private Instant windowStart;

    // ── Constructor used by OtpService when creating a row for the first time ──

    public OtpState(String email, String otpType) {
        this.email   = email;
        this.otpType = otpType;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Clear the active OTP fields without touching the rate-limit counters. */
    public void clearOtp() {
        this.otpHash        = null;
        this.otpExpiresAt   = null;
        this.otpSentAt      = null;
        this.verifyAttempts = 0;
    }

    /** Reset send-quota (called after successful verification). */
    public void resetQuota() {
        this.sendCount   = 0;
        this.windowStart = null;
    }
}
