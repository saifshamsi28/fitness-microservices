package com.saif.fitness.userservice.service;

import com.saif.fitness.userservice.dto.SignupRequest;
import com.saif.fitness.userservice.dto.SignupResponse;
import com.saif.fitness.userservice.dto.UserRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Orchestrates user registration:
 *   1. Create the user in Keycloak
 *   2. Persist user data in the local DB via UserService
 *   3. Return a SignupResponse
 *
 * Exceptions thrown (propagated to the controller for HTTP-status mapping):
 *   - KeycloakAdminService.UserAlreadyExistsException → 409
 *   - KeycloakAdminService.KeycloakUserCreationException → 500
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignupService {

    private final KeycloakAdminService keycloakAdminService;
    private final UserService          userService;

    public Mono<SignupResponse> register(SignupRequest request) {
        return Mono.fromCallable(() -> {
                    log.info("SignupService: creating Keycloak user for '{}'", request.getEmail());
                    return keycloakAdminService.createUser(
                            request.getUsername(),
                            request.getEmail(),
                            request.getPassword(),
                            request.getFirstName(),
                            request.getLastName());
                })
                .flatMap(keycloakId -> {
                    log.info("SignupService: keycloak user created [{}], persisting to DB", keycloakId);
                    UserRequestDto dto = new UserRequestDto();
                    dto.setKeycloakId(keycloakId);
                    dto.setEmail(request.getEmail());
                    dto.setFirstName(request.getFirstName());
                    dto.setLastName(request.getLastName());
                    dto.setPassword(request.getPassword());
                    dto.setUsername(request.getUsername());

                    return userService.register(dto)
                            .map(saved -> {
                                log.info("SignupService: DB entry created for '{}'", request.getEmail());
                                return SignupResponse.builder()
                                        .keycloakId(keycloakId)
                                        .email(request.getEmail())
                                        .firstName(request.getFirstName())
                                        .lastName(request.getLastName())
                                        .message("User registered successfully")
                                        .success(true)
                                        .build();
                            });
                });
    }
}
