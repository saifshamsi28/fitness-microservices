package com.saif.fitness.userservice.controller;

import com.saif.fitness.userservice.dto.SignupRequest;
import com.saif.fitness.userservice.dto.SignupResponse;
import com.saif.fitness.userservice.dto.UserRequestDto;
import com.saif.fitness.userservice.service.KeycloakAdminService;
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

    private final KeycloakAdminService keycloakAdminService;
    private final UserService userService;

    @PostMapping("/signup")
    public Mono<ResponseEntity<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        log.info("Signup request received for email: {}", request.getEmail());

        return Mono.fromCallable(() -> {
                    // Step 1: Create user in Keycloak
                    log.info("Step 1: Creating user in Keycloak");
                    String keycloakId = keycloakAdminService.createUser(
                            request.getEmail(),
                            request.getPassword(),
                            request.getFirstName(),
                            request.getLastName()
                    );
                    log.info("User created in Keycloak with ID: {}", keycloakId);
                    return keycloakId;
                })
                .flatMap(keycloakId -> {
                    // Step 2: Save user to user-service database
                    log.info("Step 2: Saving user to user-service database");

                    UserRequestDto userRequestDto = new UserRequestDto();
                    userRequestDto.setKeycloakId(keycloakId);
                    userRequestDto.setEmail(request.getEmail());
                    userRequestDto.setFirstName(request.getFirstName());
                    userRequestDto.setLastName(request.getLastName());
                    userRequestDto.setPassword(request.getPassword());

                    return userService.register(userRequestDto)
                            .map(userResponse -> {
                                log.info("User saved to database successfully");

                                // Step 3: Build success response
                                SignupResponse response = SignupResponse.builder()
                                        .keycloakId(keycloakId)
                                        .email(request.getEmail())
                                        .firstName(request.getFirstName())
                                        .lastName(request.getLastName())
                                        .message("User registered successfully")
                                        .success(true)
                                        .build();

                                return ResponseEntity.status(HttpStatus.CREATED).body(response);
                            });
                })
                .onErrorResume(e -> {
                    log.error("Signup failed: {}", e.getMessage());

                    // Determine error type and status code
                    HttpStatus status = HttpStatus.BAD_REQUEST;
                    String errorMessage;

                    if (e instanceof KeycloakAdminService.UserAlreadyExistsException) {
                        // User already exists - return 409 Conflict
                        status = HttpStatus.CONFLICT;
                        errorMessage = "Email already registered. Please login.";
                        log.warn("Signup attempt with existing email: {}", request.getEmail());

                    } else if (e instanceof KeycloakAdminService.KeycloakUserCreationException) {
                        // Keycloak creation failed
                        status = HttpStatus.INTERNAL_SERVER_ERROR;
                        errorMessage = "Failed to create user account. Please try again.";
                        log.error("Keycloak user creation failed", e);

                    } else if (e.getMessage() != null && e.getMessage().contains("password")) {
                        // Password validation error
                        errorMessage = "Password does not meet requirements.";
                        log.warn("Password validation failed for signup");

                    } else if (e.getMessage() != null && e.getMessage().contains("email")) {
                        // Email validation error
                        errorMessage = "Invalid email address.";
                        log.warn("Email validation failed for signup");

                    } else {
                        // Generic error
                        errorMessage = "Signup failed. Please try again.";
                        log.error("Unexpected signup error", e);
                    }

                    SignupResponse errorResponse = SignupResponse.builder()
                            .success(false)
                            .message(errorMessage)
                            .build();

                    return Mono.just(ResponseEntity.status(status).body(errorResponse));
                });
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<String>> health() {
        return Mono.just(ResponseEntity.ok("Auth service is running"));
    }

    /**
     * Triggers a Keycloak UPDATE_PASSWORD email.
     * Always returns 200 so attackers cannot enumerate registered emails.
     */
    @PostMapping("/forgot-password")
    public Mono<ResponseEntity<Map<String, Object>>> forgotPassword(
            @RequestBody Map<String, String> body) {

        String email = body != null ? body.get("email") : null;
        if (email == null || email.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Email is required")));
        }

        return Mono.fromRunnable(() -> keycloakAdminService.sendPasswordResetEmail(email.trim()))
                .thenReturn(ResponseEntity.ok(
                        Map.<String, Object>of("success", true,
                                "message", "If that email is registered, a reset link has been sent.")))
                .onErrorResume(e -> {
                    log.error("forgot-password endpoint error: {}", e.getMessage());
                    // Still return 200 to avoid enumeration
                    return Mono.just(ResponseEntity.ok(
                            Map.<String, Object>of("success", true,
                                    "message", "If that email is registered, a reset link has been sent.")));
                });
    }
}