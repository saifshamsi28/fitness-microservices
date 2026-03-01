package com.saif.fitness.userservice.service;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class KeycloakAdminService {

    @Value("${keycloak.server-url}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin-client-id}")
    private String adminClientId;

    @Value("${keycloak.admin-username}")
    private String adminUsername;

    @Value("${keycloak.admin-password}")
    private String adminPassword;

    @Value("${keycloak.app-client-id}")
    private String appClientId;

    public String createUser(String email, String password, String firstName, String lastName) {
        log.info("Creating user in Keycloak: {}", email);

        try (Keycloak keycloak = getKeycloakInstance()) {
            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();

            // Check if user already exists
            List<UserRepresentation> existingUsers = usersResource.search(email, true);
            if (!existingUsers.isEmpty()) {
                log.warn("User already exists in Keycloak: {}", email);
                throw new UserAlreadyExistsException("Email already registered");
            }

            // Create user representation
            UserRepresentation user = new UserRepresentation();
            user.setUsername(email);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEnabled(true);
            user.setEmailVerified(true);

            // Create user in Keycloak
            Response response = usersResource.create(user);
            int statusCode = response.getStatus();

            // Handle different response codes
            if (statusCode == 201) {
                // Success - extract user ID
                String location = response.getLocation().getPath();
                String userId = location.substring(location.lastIndexOf('/') + 1);
                log.info("User created in Keycloak with ID: {}", userId);

                // Set password
                setUserPassword(usersResource, userId, password);

                return userId;

            } else if (statusCode == 409) {
                // Conflict - user already exists
                log.warn("Keycloak returned 409: User already exists");
                String errorMessage = response.readEntity(String.class);
                log.warn("Keycloak error details: {}", errorMessage);
                throw new UserAlreadyExistsException("Email already registered");

            } else {
                // Other error
                log.error("Failed to create user in Keycloak. Status: {}", statusCode);
                String errorMessage = response.readEntity(String.class);
                log.error("Error details: {}", errorMessage);
                throw new KeycloakUserCreationException("Failed to create user in Keycloak");
            }

        } catch (UserAlreadyExistsException e) {
            // Re-throw this specific exception
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error creating user in Keycloak: {}", e.getMessage(), e);
            throw new KeycloakUserCreationException("Failed to create user: " + e.getMessage());
        }
    }

    private void setUserPassword(UsersResource usersResource, String userId, String password) {
        log.info("Setting password for user: {}", userId);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);

        usersResource.get(userId).resetPassword(credential);
        log.info("Password set successfully for user: {}", userId);
    }

    private Keycloak getKeycloakInstance() {
        return KeycloakBuilder.builder()
                .serverUrl(keycloakServerUrl)
                .realm("master")
                .clientId(adminClientId)
                .username(adminUsername)
                .password(adminPassword)
                .build();
    }

    public boolean userExists(String email) {
        try (Keycloak keycloak = getKeycloakInstance()) {
            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> users = usersResource.search(email, true);
            return !users.isEmpty();

        } catch (Exception e) {
            log.error("Error checking if user exists: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sends a Keycloak UPDATE_PASSWORD action email to the user with the given email.
     * Searches by EMAIL field (not username) so users whose username ≠ email are found correctly.
     * Silently succeeds even if no matching account is found (security best practice).
     */
    public void sendPasswordResetEmail(String email) {
        try (Keycloak keycloak = getKeycloakInstance()) {
            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();

            // Search by email field specifically (not username)
            List<UserRepresentation> users = usersResource.searchByEmail(email, true);
            if (users.isEmpty()) {
                log.warn("Password reset requested for unregistered email: {}", email);
                return; // silent — don't reveal whether email exists
            }

            String userId = users.get(0).getId();
            usersResource.get(userId).executeActionsEmail(appClientId, null, List.of("UPDATE_PASSWORD"));
            log.info("Password reset email dispatched for user: {}", email);

        } catch (Exception e) {
            log.error("Failed to send password reset email for {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Failed to send password reset email");
        }
    }

    public static class UserAlreadyExistsException extends RuntimeException {
        public UserAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class KeycloakUserCreationException extends RuntimeException {
        public KeycloakUserCreationException(String message) {
            super(message);
        }
    }
}