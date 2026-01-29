package com.saif.fitnessapp.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.saif.fitnessapp.MainActivity;
import com.saif.fitnessapp.R;
import com.saif.fitnessapp.auth.AuthManager;
import com.saif.fitnessapp.auth.TokenManager;

import dagger.hilt.android.AndroidEntryPoint;

import javax.inject.Inject;

@AndroidEntryPoint
public class LoginActivity extends AppCompatActivity {

    @Inject
    AuthManager authManager;

    @Inject
    TokenManager tokenManager;

    private Button loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        loginButton = findViewById(R.id.login_button);

        loginButton.setOnClickListener(v -> {
            Intent loginIntent = authManager.getLoginIntent();
            startActivity(loginIntent);
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleAuthCallback(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if user has logged in via the browser
        if (tokenManager.isLoggedIn()) {
            navigateToMain();
        }
    }

    private void handleAuthCallback(Intent intent) {
        if (intent != null && intent.getData() != null) {
            String authorizationCode = intent.getData().getQueryParameter("code");
            
            if (authorizationCode != null) {
                // Exchange code for token (you'll need to provide your Keycloak client secret)
                authManager.exchangeCodeForToken(authorizationCode, "YOUR_CLIENT_SECRET", 
                    new AuthManager.AuthTokenCallback() {
                        @Override
                        public void onSuccess(String userId) {
                            navigateToMain();
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(LoginActivity.this, "Login failed: " + error, 
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
            }
        }
    }

    private void navigateToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
