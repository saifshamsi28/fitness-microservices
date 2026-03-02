package com.saif.fitness.userservice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    // ── Public API ────────────────────────────────────────────────────────────

    @Async
    public void sendSignupVerificationOtp(String to, String firstName, String otp) {
        String subject = "Fitness App — Verify your email address";
        String body = buildOtpEmail(
                firstName,
                otp,
                "Verify Your Email",
                "You requested email verification for your Fitness App account. "
                + "Use the code below to complete your registration.",
                "This code expires in <strong>10 minutes</strong>. "
                + "If you did not create a Fitness App account, you can safely ignore this email."
        );
        send(to, subject, body);
    }

    @Async
    public void sendPasswordResetOtp(String to, String firstName, String otp) {
        String subject = "Fitness App — Password reset code";
        String body = buildOtpEmail(
                firstName,
                otp,
                "Reset Your Password",
                "We received a request to reset the password for your Fitness App account.",
                "This code expires in <strong>10 minutes</strong>. "
                + "If you did not request a password reset, you can safely ignore this email — "
                + "your account is secure."
        );
        send(to, subject, body);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom("Fitness App <" + fromAddress + ">");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("EMAIL_SENT subject='{}'", subject);
        } catch (MessagingException ex) {
            log.error("EMAIL_FAILED subject='{}' error={}", subject, ex.getMessage());
        }
    }

    private String buildOtpEmail(String name, String otp, String heading, String intro, String footer) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width,initial-scale=1"/>
              <title>Fitness App</title>
            </head>
            <body style="margin:0;padding:0;background:#f0f4ff;font-family:'Helvetica Neue',Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f0f4ff;padding:40px 16px;">
                <tr><td align="center">
                  <table width="520" cellpadding="0" cellspacing="0"
                         style="background:#ffffff;border-radius:16px;overflow:hidden;
                                box-shadow:0 4px 24px rgba(0,0,0,0.08);">

                    <!-- Header -->
                    <tr>
                      <td style="background:#1565C0;padding:28px 40px;">
                        <p style="margin:0;font-size:22px;font-weight:700;color:#ffffff;letter-spacing:-0.5px;">
                          💪 Fitness<span style="color:#90CAF9;">App</span>
                        </p>
                      </td>
                    </tr>

                    <!-- Body -->
                    <tr>
                      <td style="padding:40px 40px 32px;">
                        <h1 style="margin:0 0 12px;font-size:22px;font-weight:700;color:#1a1a2e;letter-spacing:-0.3px;">%s</h1>
                        <p style="margin:0 0 28px;font-size:15px;line-height:1.6;color:#52525b;">
                          Hi %s,<br/><br/>%s
                        </p>

                        <!-- OTP block -->
                        <div style="background:#f0f4ff;border:1px solid #c5cae9;border-radius:12px;
                                    padding:28px 24px;text-align:center;margin-bottom:28px;">
                          <p style="margin:0 0 16px;font-size:11px;font-weight:600;color:#5c6bc0;
                                    text-transform:uppercase;letter-spacing:2px;">
                            Your verification code
                          </p>
                          <div style="display:inline-block;background:#e8eaf6;
                                      border:2px solid #3f51b5;border-radius:10px;
                                      padding:14px 32px;">
                            <span style="font-size:40px;font-weight:900;
                                         font-family:'Courier New',Courier,monospace;
                                         color:#1a237e;letter-spacing:10px;">%s</span>
                          </div>
                          <p style="margin:12px 0 0;font-size:12px;color:#9e9e9e;">
                            Enter this code in the app to continue
                          </p>
                        </div>

                        <p style="margin:0;font-size:13px;line-height:1.6;color:#71717a;">%s</p>
                      </td>
                    </tr>

                    <!-- Footer -->
                    <tr>
                      <td style="background:#f8f9fe;border-top:1px solid #e8eaf6;
                                 padding:20px 40px;text-align:center;">
                        <p style="margin:0;font-size:12px;color:#a1a1aa;">
                          &copy; 2026 Fitness App &bull; This is an automated message, please do not reply.
                        </p>
                      </td>
                    </tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(heading, name, intro, otp, footer);
    }
}
