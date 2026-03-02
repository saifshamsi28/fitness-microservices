package com.saif.fitness.userservice.controller;

import com.saif.fitness.userservice.dto.ChangePasswordRequestDto;
import com.saif.fitness.userservice.dto.UpdateProfileRequestDto;
import com.saif.fitness.userservice.dto.UserResponseDto;
import com.saif.fitness.userservice.service.OtpService;
import com.saif.fitness.userservice.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponseDto> getUser(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getUser(userId));
    }

    @GetMapping("/{userId}/validate")
    public ResponseEntity<Boolean> validateUser(@PathVariable String userId, HttpServletRequest req) {
        return ResponseEntity.ok(userService.existsByKeYCloakUserId(userId));
    }

    /** Update name and/or email */
    @PutMapping("/{userId}")
    public ResponseEntity<?> updateUser(
            @PathVariable String userId,
            @RequestBody UpdateProfileRequestDto request) {
        try {
            UserResponseDto updated = userService.updateUser(userId, request);
            return ResponseEntity.ok(updated);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("updateUser error for {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to update profile"));
        }
    }

    /** Change password — verifies current password before setting the new one */
    @PostMapping("/{userId}/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @PathVariable String userId,
            @RequestBody ChangePasswordRequestDto request) {
        try {
            userService.changePassword(userId, request);
            return ResponseEntity.ok(Map.of("success", true, "message", "Password changed successfully"));
        } catch (UserService.InvalidPasswordException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("changePassword error for {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to change password"));
        }
    }

    /**
     * Check email + username availability before sending an email-change OTP.
     * Requires a valid JWT for the user making the request.
     */
    @PostMapping("/{userId}/send-email-change-otp")
    public ResponseEntity<Map<String, Object>> sendEmailChangeOtp(
            @PathVariable String userId,
            @RequestBody Map<String, String> body) {
        String newEmail = body != null ? body.get("email") : null;
        if (newEmail == null || newEmail.isBlank())
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Email is required"));
        try {
            OtpService.SendOtpResult result =
                    userService.sendEmailChangeOtp(userId, newEmail.trim().toLowerCase());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "OTP sent to " + newEmail,
                    "sendCount", result.sendCount,
                    "maxSends",  result.maxSends,
                    "windowResetInSeconds", result.windowResetInSeconds));
        } catch (UserService.EmailAlreadyTakenException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (OtpService.CooldownException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("success", false, "message", e.getMessage(),
                            "retryAfterSeconds", e.getRetryAfterSeconds(),
                            "sendCount", e.getSendCount(), "maxSends", e.getMaxSends()));
        } catch (OtpService.RateLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("success", false, "message", e.getMessage(),
                            "retryAfterSeconds", e.getRetryAfterSeconds(),
                            "sendCount", e.getSendCount(), "maxSends", e.getMaxSends()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("sendEmailChangeOtp error for {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to send OTP"));
        }
    }}