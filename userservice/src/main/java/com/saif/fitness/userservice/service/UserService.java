package com.saif.fitness.userservice.service;

import com.saif.fitness.userservice.dto.ChangePasswordRequestDto;
import com.saif.fitness.userservice.dto.UpdateProfileRequestDto;
import com.saif.fitness.userservice.dto.UserRequestDto;
import com.saif.fitness.userservice.dto.UserResponseDto;
import com.saif.fitness.userservice.models.User;
import com.saif.fitness.userservice.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final EmailVerificationService emailVerificationService;
    public Mono<UserResponseDto> register(UserRequestDto userRequest) {
        return Mono.fromCallable(() -> {
            log.info("In USER-SERVICE/UserService/register, request: {}", userRequest);

            User user;

            // Check if user already exists by email
            if (userRepository.existsByEmail(userRequest.getEmail())) {
                log.info("User already exists by email: {}", userRequest.getEmail());
                user = userRepository.findByEmail(userRequest.getEmail());

                UserResponseDto responseDto = UserResponseDto.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .username(user.getUsername())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .keycloakId(user.getKeycloakId())
                        .createdAt(user.getCreatedAt())
                        .updatedAt(user.getUpdatedAt())
                        .build();

                log.info("In USER-SERVICE/UserService/existByEmail check, response: {}", responseDto);
                return responseDto;
            }

            // Double-check username uniqueness at registration time (race-condition guard)
            String normalUsername = (userRequest.getUsername() != null && !userRequest.getUsername().isBlank())
                    ? userRequest.getUsername().trim().toLowerCase() : null;
            if (normalUsername != null && userRepository.existsByUsername(normalUsername)) {
                throw new UsernameAlreadyTakenException("Username \"" + userRequest.getUsername().trim() + "\" is already taken.");
            }

            // Create new user
            user = new User();
            user.setEmail(userRequest.getEmail());
            user.setFirstName(userRequest.getFirstName());
            user.setLastName(userRequest.getLastName());
            user.setPassword(userRequest.getPassword());
            user.setKeycloakId(userRequest.getKeycloakId());
            if (userRequest.getUsername() != null && !userRequest.getUsername().isBlank())
                user.setUsername(userRequest.getUsername().trim().toLowerCase());

            user = userRepository.save(user);

            UserResponseDto userResponseDto = UserResponseDto.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .username(user.getUsername())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .createdAt(user.getCreatedAt())
                    .updatedAt(user.getUpdatedAt())
                    .keycloakId(user.getKeycloakId())
                    .build();

            log.info("In USER-SERVICE/UserService/ user created successfully, response: {}", userResponseDto);
            return userResponseDto;
        });
    }

    public UserResponseDto getUser(String userId) {
        User user = userRepository.findByKeycloakId(userId).orElseThrow(
                () -> new EntityNotFoundException("User not found with id: " + userId));

        return UserResponseDto.builder()
                .id(userId)
                .email(user.getEmail())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .createdAt(user.getCreatedAt())
                .keycloakId(user.getKeycloakId())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public Boolean existsByKeYCloakUserId(String keycloakId) {
        return userRepository.existsByKeycloakId(keycloakId);
    }

    /**
     * Updates the user's profile (firstName, lastName, email) in both the DB and Keycloak.
     */
    public UserResponseDto updateUser(String keycloakUserId, UpdateProfileRequestDto request) {
        User user = userRepository.findByKeycloakId(keycloakUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (request.getFirstName() != null && !request.getFirstName().isBlank())
            user.setFirstName(request.getFirstName());
        if (request.getLastName() != null && !request.getLastName().isBlank())
            user.setLastName(request.getLastName());
        if (request.getEmail() != null && !request.getEmail().isBlank())
            user.setEmail(request.getEmail());

        user = userRepository.save(user);

        // Mirror the changes to Keycloak
        keycloakAdminService.updateUserProfile(
                keycloakUserId,
                request.getFirstName(),
                request.getLastName(),
                request.getEmail()
        );

        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .keycloakId(user.getKeycloakId())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * Verifies the current password via Keycloak ROPC, then sets the new one.
     */
    public void changePassword(String keycloakUserId, ChangePasswordRequestDto request) {
        User user = userRepository.findByKeycloakId(keycloakUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        boolean valid = keycloakAdminService.verifyPassword(user.getEmail(), request.getCurrentPassword());
        if (!valid) throw new InvalidPasswordException("Current password is incorrect");

        keycloakAdminService.setNewPassword(user.getEmail(), request.getNewPassword());
        log.info("Password changed for keycloakUserId={}", keycloakUserId);
    }

    public static class InvalidPasswordException extends RuntimeException {
        public InvalidPasswordException(String msg) { super(msg); }
    }

    /**
     * Pre-registration check: throws typed exceptions if email or username is already taken.
     * Called from {@code AuthController.signupSendOtp} before sending any OTP.
     */
    public void checkSignupAvailability(String email, String username) {
        if (userRepository.existsByEmail(email.trim().toLowerCase())) {
            throw new EmailAlreadyTakenException("That email is already registered. Please log in.");
        }
        if (username != null && !username.isBlank()
                && userRepository.existsByUsername(username.trim().toLowerCase())) {
            throw new UsernameAlreadyTakenException("Username \"" + username.trim() + "\" is already taken. Please choose another.");
        }
    }

    public static class UsernameAlreadyTakenException extends RuntimeException {
        public UsernameAlreadyTakenException(String msg) { super(msg); }
    }

    /**
     * Checks that newEmail is not already taken, then sends an OTP to that address.
     * Throws {@link EmailAlreadyTakenException} (→ 409) before touching the OTP table.
     */
    public OtpService.SendOtpResult sendEmailChangeOtp(String keycloakUserId, String newEmail) {
        // 1. Reject if another account already uses that email
        User existing = userRepository.findByEmail(newEmail);
        if (existing != null && !existing.getKeycloakId().equals(keycloakUserId)) {
            throw new EmailAlreadyTakenException("That email is already registered. Please use a different one.");
        }
        // 2. Get current user's firstName for the email template
        User user = userRepository.findByKeycloakId(keycloakUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        // 3. Send OTP
        return emailVerificationService.sendOtp(newEmail, user.getFirstName());
    }

    public static class EmailAlreadyTakenException extends RuntimeException {
        public EmailAlreadyTakenException(String msg) { super(msg); }
    }
}