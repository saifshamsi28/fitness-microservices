package com.saif.fitnessapp.auth;

public class AuthConfig {
    // Keycloak Configuration
    public static final String KEYCLOAK_SERVER_URL = "https://your-keycloak-server.com"; // Replace with actual URL
    public static final String KEYCLOAK_REALM = "fitness-realm"; // Replace with your realm
    public static final String CLIENT_ID = "fitness-mobile-app"; // Replace with your client ID
    public static final String REDIRECT_URL = "com.saif.fitnessapp://oauth-callback";
    public static final String SCOPE = "openid profile email";

    // Authorization Endpoint
    public static final String AUTHORIZATION_ENDPOINT = KEYCLOAK_SERVER_URL + "/auth/realms/" + KEYCLOAK_REALM + "/protocol/openid-connect/auth";
    public static final String TOKEN_ENDPOINT = KEYCLOAK_SERVER_URL + "/auth/realms/" + KEYCLOAK_REALM + "/protocol/openid-connect/token";
    public static final String LOGOUT_ENDPOINT = KEYCLOAK_SERVER_URL + "/auth/realms/" + KEYCLOAK_REALM + "/protocol/openid-connect/logout";
    public static final String USERINFO_ENDPOINT = KEYCLOAK_SERVER_URL + "/auth/realms/" + KEYCLOAK_REALM + "/protocol/openid-connect/userinfo";

    // API Configuration
    public static final String API_BASE_URL = "http://192.168.x.x:8080/"; // Replace with your Gateway IP/domain
    public static final String API_TIMEOUT_SECONDS = "30";
}
