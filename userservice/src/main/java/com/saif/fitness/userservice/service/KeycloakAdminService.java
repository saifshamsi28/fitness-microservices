package com.saif.fitness.userservice.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.WebApplicationException;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Value("${keycloak.password-reset-redirect-uri}")
    private String passwordResetRedirectUri;

    public String createUser(String username, String email, String password, String firstName, String lastName) {
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
            user.setUsername(username);
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
            log.info("Calling executeActionsEmail: clientId={}, redirectUri={}, userId={}", appClientId, passwordResetRedirectUri, userId);
            usersResource.get(userId).executeActionsEmail(appClientId, passwordResetRedirectUri, List.of("UPDATE_PASSWORD"));
            log.info("Password reset email dispatched for user: {}", email);

        } catch (WebApplicationException e) {
            // Extract actual Keycloak error response body for detailed diagnosis
            String body = "<no body>";
            try {
                Response response = e.getResponse();
                response.bufferEntity();
                body = response.readEntity(String.class);
            } catch (Exception ignored) {}
            log.error("Keycloak executeActionsEmail failed for {} — HTTP {}: {}", email, e.getResponse().getStatus(), body);
            throw new RuntimeException("Failed to send password reset email: " + body);
        } catch (Exception e) {
            log.error("Failed to send password reset email for {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Failed to send password reset email");
        }
    }

    /**
     * Sets a new password for the user with the given email via Keycloak Admin API.
     * Used by the OTP-based forgot-password reset flow.
     */
    public void setNewPassword(String email, String newPassword) {
        try (Keycloak keycloak = getKeycloakInstance()) {
            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> users = usersResource.searchByEmail(email, true);
            if (users.isEmpty()) {
                log.warn("setNewPassword: no user found for email {}", email);
                throw new RuntimeException("User not found");
            }

            String userId = users.get(0).getId();
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(newPassword);
            credential.setTemporary(false);
            usersResource.get(userId).resetPassword(credential);
            log.info("Password updated for user: {}", email);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("setNewPassword error for {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Failed to update password");
        }
    }

    /**
     * Updates firstName, lastName, and/or email of an existing Keycloak user.
     *
     * Strategy: GET the current user JSON via the Admin REST API, mutate only the
     * requested fields in the raw Map, then PUT the same Map back.  This guarantees
     * Keycloak receives exactly what it originally returned — avoiding the 400 that
     * the Resteasy admin-client triggers by serialising every null field in
     * UserRepresentation on the way out.
     */
    public void updateUserProfile(String keycloakUserId, String firstName, String lastName, String email) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String adminToken = getAdminToken();
            String userUrl    = keycloakServerUrl + "/admin/realms/" + realm + "/users/" + keycloakUserId;

            // ── Step 1: GET current user as a raw Map ───────────────────────
            HttpHeaders getHeaders = new HttpHeaders();
            getHeaders.setBearerAuth(adminToken);
            @SuppressWarnings("unchecked")
            ResponseEntity<Map> getResp = restTemplate.exchange(
                    userUrl, HttpMethod.GET, new HttpEntity<>(getHeaders), Map.class);
            if (getResp.getBody() == null)
                throw new RuntimeException("Empty response when fetching user from Keycloak");

            @SuppressWarnings("unchecked")
            Map<String, Object> userMap = new LinkedHashMap<>(getResp.getBody());

            // ── Step 2: mutate only the fields being changed ─────────────────
            if (firstName != null && !firstName.isBlank()) userMap.put("firstName", firstName);
            if (lastName  != null && !lastName.isBlank())  userMap.put("lastName",  lastName);
            if (email     != null && !email.isBlank()) {
                userMap.put("email",         email);
                userMap.put("emailVerified", true);
            }

            // ── Step 3: PUT the modified map back ────────────────────────────
            HttpHeaders putHeaders = new HttpHeaders();
            putHeaders.setContentType(MediaType.APPLICATION_JSON);
            putHeaders.setBearerAuth(adminToken);
            restTemplate.put(userUrl, new HttpEntity<>(userMap, putHeaders));

            log.info("Keycloak profile updated for userId={}", keycloakUserId);

        } catch (Exception e) {
            log.error("updateUserProfile error for {}: {}", keycloakUserId, e.getMessage(), e);
            throw new RuntimeException("Failed to update profile in Keycloak: " + e.getMessage());
        }
    }

    /**
     * Obtains a short-lived admin access token using the master-realm admin credentials.
     * Used for direct Admin REST API calls where the Resteasy proxy would serialise
     * more fields than Keycloak accepts.
     */
    private String getAdminToken() {
        RestTemplate restTemplate = new RestTemplate();
        String tokenUrl = keycloakServerUrl + "/realms/master/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "password");
        params.add("client_id",  "admin-cli");
        params.add("username",   adminUsername);
        params.add("password",   adminPassword);

        @SuppressWarnings("unchecked")
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                tokenUrl, new HttpEntity<>(params, headers), Map.class);
        if (resp.getBody() == null || !resp.getBody().containsKey("access_token"))
            throw new RuntimeException("Failed to obtain admin token from Keycloak");
        return (String) resp.getBody().get("access_token");
    }

    /**
     * Verifies the user's current password by attempting an ROPC token request.
     * Returns true if the password is correct, false if it is wrong (401).
     */
    public boolean verifyPassword(String email, String password) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String tokenUrl = keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type",    "password");
            params.add("client_id",     appClientId);
            params.add("username",      email);
            params.add("password",      password);

            restTemplate.postForEntity(tokenUrl, new HttpEntity<>(params, headers), String.class);
            return true;
        } catch (HttpClientErrorException.Unauthorized e) {
            return false;
        } catch (Exception e) {
            log.error("verifyPassword error for {}: {}", email, e.getMessage());
            throw new RuntimeException("Could not verify current password");
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