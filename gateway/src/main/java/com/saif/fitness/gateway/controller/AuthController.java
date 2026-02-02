package com.saif.fitness.gateway.controller;

import com.saif.fitness.gateway.dto.SignupRequest;
import com.saif.fitness.gateway.dto.SignupResponse;
import com.saif.fitness.gateway.service.KeycloakAdminService;
import com.saif.fitness.gateway.user.UserRequestDto;
import com.saif.fitness.gateway.user.UserResponseDto;
import com.saif.fitness.gateway.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Authentication Controller
 * Handles signup and authentication-related endpoints
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final KeycloakAdminService keycloakAdminService;
    private final UserService userService;

    /**
     * Signup endpoint
     * Creates user in Keycloak and then saves to user-service database
     * 
     * POST /api/auth/signup
     * 
     * Request Body:
     * {
     *   "email": "user@example.com",
     *   "password": "Saif@1234",
     *   "firstName": "John",
     *   "lastName": "Doe"
     * }
     * 
     * Response:
     * {
     *   "keycloakId": "uuid",
     *   "email": "user@example.com",
     *   "firstName": "John",
     *   "lastName": "Doe",
     *   "message": "User registered successfully",
     *   "success": true
     * }
     */
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

            return userService.registerUser(userRequestDto)
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
            log.error("Signup failed: {}", e.getMessage(), e);
            
            // Build error response
            SignupResponse errorResponse = SignupResponse.builder()
                    .success(false)
                    .message(getErrorMessage(e))
                    .build();

            // Determine HTTP status based on error type
            HttpStatus status = HttpStatus.BAD_REQUEST;
            if (e.getMessage() != null && e.getMessage().contains("already registered")) {
                status = HttpStatus.CONFLICT;
            }

            return Mono.just(ResponseEntity.status(status).body(errorResponse));
        });
    }

    /**
     * Extract user-friendly error message from exception
     */
    private String getErrorMessage(Throwable e) {
        String message = e.getMessage();
        
        if (message == null) {
            return "Signup failed. Please try again.";
        }
        
        // Handle specific error cases
        if (message.contains("already registered") || message.contains("already exists")) {
            return "Email already registered. Please login.";
        }
        
        if (message.contains("password")) {
            return "Password does not meet requirements.";
        }
        
        if (message.contains("email")) {
            return "Invalid email address.";
        }
        
        // Generic error message
        return "Signup failed. Please try again.";
    }

    /**
     * Health check endpoint for auth service
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<String>> health() {
        return Mono.just(ResponseEntity.ok("Auth service is running"));
    }
}