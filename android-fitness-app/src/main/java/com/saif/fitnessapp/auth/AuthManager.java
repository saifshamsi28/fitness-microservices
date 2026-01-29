package com.saif.fitnessapp.auth;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.browser.customtabs.CustomTabsIntent;

import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ClientAuthentication;
import net.openid.appauth.ClientSecretBasic;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.TokenRequest;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AuthManager {
    private final Context context;
    private final TokenManager tokenManager;
    private AuthorizationService authorizationService;

    @Inject
    public AuthManager(Context context, TokenManager tokenManager) {
        this.context = context;
        this.tokenManager = tokenManager;
    }

    public void initializeAuthService() {
        if (authorizationService == null) {
            authorizationService = new AuthorizationService(context);
        }
    }

    public Intent getLoginIntent() {
        initializeAuthService();

        AuthorizationServiceConfiguration authConfig = new AuthorizationServiceConfiguration(
                Uri.parse(AuthConfig.AUTHORIZATION_ENDPOINT),
                Uri.parse(AuthConfig.TOKEN_ENDPOINT),
                null,
                Uri.parse(AuthConfig.LOGOUT_ENDPOINT)
        );

        AuthorizationRequest authRequest = new AuthorizationRequest.Builder(
                authConfig,
                AuthConfig.CLIENT_ID,
                ResponseTypeValues.CODE,
                Uri.parse(AuthConfig.REDIRECT_URL)
        )
                .setScopes(AuthConfig.SCOPE.split(" "))
                .build();

        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
        return authorizationService.getAuthorizationRequestIntent(authRequest, customTabsIntent);
    }

    public void exchangeCodeForToken(String authorizationCode, String clientSecret, AuthTokenCallback callback) {
        initializeAuthService();

        AuthorizationServiceConfiguration authConfig = new AuthorizationServiceConfiguration(
                Uri.parse(AuthConfig.AUTHORIZATION_ENDPOINT),
                Uri.parse(AuthConfig.TOKEN_ENDPOINT),
                null,
                Uri.parse(AuthConfig.LOGOUT_ENDPOINT)
        );

        TokenRequest tokenRequest = new TokenRequest.Builder(
                authConfig,
                AuthConfig.CLIENT_ID
        )
                .setAuthorizationCode(authorizationCode)
                .setRedirectUri(Uri.parse(AuthConfig.REDIRECT_URL))
                .build();

        ClientAuthentication clientAuth = new ClientSecretBasic(clientSecret);

        authorizationService.performTokenRequest(tokenRequest, clientAuth, (response, ex) -> {
            if (response != null) {
                String accessToken = response.accessToken;
                String refreshToken = response.refreshToken;
                String idToken = response.idToken;
                long expiresIn = response.accessTokenExpirationTime != null ? 
                        (response.accessTokenExpirationTime - System.currentTimeMillis()) / 1000 : 3600;

                // Extract userId from idToken (decode JWT)
                String userId = extractUserIdFromToken(idToken);

                tokenManager.saveTokens(accessToken, refreshToken, idToken, expiresIn, "Bearer", userId);
                callback.onSuccess(userId);
            } else {
                callback.onError(ex != null ? ex.getMessage() : "Token exchange failed");
            }
        });
    }

    public void refreshAccessToken(String refreshToken, AuthTokenCallback callback) {
        initializeAuthService();

        AuthorizationServiceConfiguration authConfig = new AuthorizationServiceConfiguration(
                Uri.parse(AuthConfig.AUTHORIZATION_ENDPOINT),
                Uri.parse(AuthConfig.TOKEN_ENDPOINT),
                null,
                Uri.parse(AuthConfig.LOGOUT_ENDPOINT)
        );

        TokenRequest tokenRequest = new TokenRequest.Builder(
                authConfig,
                AuthConfig.CLIENT_ID
        )
                .setRefreshToken(refreshToken)
                .build();

        authorizationService.performTokenRequest(tokenRequest, (response, ex) -> {
            if (response != null) {
                String accessToken = response.accessToken;
                String newRefreshToken = response.refreshToken != null ? response.refreshToken : refreshToken;
                String idToken = response.idToken;
                long expiresIn = response.accessTokenExpirationTime != null ? 
                        (response.accessTokenExpirationTime - System.currentTimeMillis()) / 1000 : 3600;

                String userId = extractUserIdFromToken(idToken);
                tokenManager.saveTokens(accessToken, newRefreshToken, idToken, expiresIn, "Bearer", userId);
                callback.onSuccess(userId);
            } else {
                callback.onError(ex != null ? ex.getMessage() : "Token refresh failed");
            }
        });
    }

    public void logout() {
        tokenManager.clearTokens();
        if (authorizationService != null) {
            authorizationService.dispose();
            authorizationService = null;
        }
    }

    private String extractUserIdFromToken(String idToken) {
        if (idToken == null) return null;
        
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length == 3) {
                String payload = new String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE));
                com.google.gson.JsonObject json = new com.google.gson.JsonParser().parse(payload).getAsJsonObject();
                if (json.has("sub")) {
                    return json.get("sub").getAsString();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public interface AuthTokenCallback {
        void onSuccess(String userId);
        void onError(String error);
    }
}
