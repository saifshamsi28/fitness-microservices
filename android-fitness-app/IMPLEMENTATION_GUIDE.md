# Android App Implementation Guide

This document provides detailed step-by-step instructions to finalize and run the Fitness Tracker Android app.

## Phase 1: Pre-Build Configuration

### Step 1: Update Keycloak Configuration
File: `src/main/java/com/saif/fitnessapp/auth/AuthConfig.java`

Replace the following values with your actual Keycloak server details:

```java
// Example: If your Keycloak is at https://auth.example.com
public static final String KEYCLOAK_SERVER_URL = "https://your-keycloak-server.com";
public static final String KEYCLOAK_REALM = "fitness-realm";  // Your realm name
public static final String CLIENT_ID = "fitness-mobile-app"; // Your client ID
public static final String API_BASE_URL = "http://192.168.1.100:8080/"; // Your Gateway IP
```

### Step 2: Add Missing Icon Resources

Create vector drawable files for navigation icons in `src/main/res/drawable/`:

**ic_home.xml:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z" />
</vector>
```

**ic_activity.xml:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M3,13h2v8H3zm4,-8h2v16H7zm4,-2h2v18h-2zm4,4h2v14h-2zm4,-2h2v16h-2z" />
</vector>
```

**ic_recommendations.xml:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z" />
</vector>
```

**ic_profile.xml:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z" />
</vector>
```

**ic_add.xml:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z" />
</vector>
```

### Step 3: Update Application Gradle Configuration

In `build.gradle.kts`, ensure the application ID and namespace match your package:

```kotlin
android {
    namespace = "com.saif.fitnessapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.saif.fitnessapp"
        // ... rest of configuration
    }
}
```

### Step 4: Handle Keycloak Client Secret

In `LoginActivity.java`, replace the placeholder with your actual client secret:

```java
// In handleAuthCallback() method
authManager.exchangeCodeForToken(
    authorizationCode, 
    "YOUR_ACTUAL_CLIENT_SECRET_HERE",  // Replace this!
    new AuthManager.AuthTokenCallback() { ... }
);
```

For production, consider storing this in a BuildConfig or secure configuration file instead of hardcoding.

## Phase 2: Keycloak Server Setup

### Step 1: Create Realm in Keycloak

1. Access Keycloak Admin Console: `http://your-keycloak-server:8080/admin`
2. Create new realm: `fitness-realm`
3. Enable "Enabled" toggle

### Step 2: Create OAuth2 Client

1. Go to Realm Settings > Clients > Create New
2. Configure:
   - Client ID: `fitness-mobile-app`
   - Client Protocol: `openid-connect`
   - Access Type: `public` (for mobile apps)
   - Standard Flow Enabled: ON
   - Direct Access Grants Enabled: ON
   - Implicit Flow Enabled: OFF
   
3. Set Redirect URIs:
   - `com.saif.fitnessapp://oauth-callback`
   
4. Set Web Origins:
   - `http://192.168.1.100:8080`

### Step 3: Generate Client Credentials

If using confidential client (for backend server communication):
1. Go to Credentials tab
2. Generate new secret
3. Copy and save the Client Secret

### Step 4: Create Test User

1. Go to Users > Add user
2. Set username and email
3. Set password (not temporary)
4. Assign roles if needed

## Phase 3: Backend Service Configuration

### Step 1: Verify Gateway is Running

The Gateway should be accessible at your configured API_BASE_URL. Test with:

```bash
curl http://192.168.1.100:8080/actuator/health
```

### Step 2: Verify Keycloak Integration

The Gateway should have JWT validation configured:

```bash
curl -H "Authorization: Bearer <valid-token>" \
  http://192.168.1.100:8080/user-service/api/users/{userId}
```

### Step 3: Check User Service

Ensure user auto-creation is enabled on first login:
- User-Service should have GET `/api/users/{userId}` endpoint
- Should auto-create users from Keycloak JWT claims

## Phase 4: Build and Deploy

### Step 1: Build for Debug

```bash
# From android-fitness-app directory
./gradlew assembleDebug
```

Output: `build/outputs/apk/debug/app-debug.apk`

### Step 2: Install on Emulator/Device

```bash
# Using adb
adb install build/outputs/apk/debug/app-debug.apk

# Or drag-drop in Android Studio
```

### Step 3: Configure Emulator Network

If using Android Emulator to connect to local backend:

```bash
# Use 10.0.2.2 to reach host machine from emulator
# Update AuthConfig.java:
public static final String API_BASE_URL = "http://10.0.2.2:8080/";
```

## Phase 5: Testing the App

### Test Login Flow
1. Launch app on emulator/device
2. Click "Login with Keycloak"
3. Browser opens with Keycloak login
4. Enter test user credentials
5. Authorize app
6. Redirected back to app

### Test Activity Tracking
1. On Home screen, click "Add Activity"
2. Select activity type
3. Enter duration and calories
4. Click Submit
5. Verify toast message "Activity tracked successfully!"

### Test Activity History
1. Navigate to Activity tab
2. Verify activities list appears
3. Pull down to refresh
4. Scroll to load more (pagination)

### Test Recommendations
1. Navigate to AI Recommendations tab
2. If no recommendations yet, see empty state
3. Complete 1-2 activities first
4. Wait for Kafka processing (check backend logs)
5. Return to Recommendations tab to see results

### Test Profile
1. Navigate to Profile tab
2. Verify user info is displayed
3. Click Logout to test logout flow

## Phase 6: Troubleshooting

### App Crashes on Launch

**Check logs:**
```bash
adb logcat | grep FitnessApp
```

**Common issues:**
- Missing Hilt annotation processor: Rebuild project
- Network connection: Verify API_BASE_URL is correct
- Keycloak config: Check AuthConfig.java values

### Login Fails

**Verify:**
1. Keycloak server is running
2. Client ID matches Keycloak config
3. Redirect URI is registered in Keycloak
4. Network connectivity to Keycloak server

**Check logs:**
```bash
adb logcat | grep AuthManager
```

### API Calls Return 401

**Check:**
1. Token is being stored: Check EncryptedSharedPreferences
2. Token is not expired
3. Authorization header is being sent
4. Backend JWT validation is correct

**Add debug logging in AuthInterceptor:**
```java
Log.d("AuthInterceptor", "Adding token: " + accessToken);
```

### Activities Not Showing

**Verify:**
1. Activities were actually created in backend
2. GET `/activity-service/api/activities` endpoint is working
3. User ID from JWT matches stored activities
4. Check backend logs for errors

### Recommendations Not Appearing

**Check:**
1. Kafka is processing recommendations
2. Backend logs show recommendation generation
3. Wait a few seconds after adding activities
4. Check AI Service logs: `docker logs ai-service`

## Phase 7: Production Deployment

### Step 1: Create Release Build

```bash
# Generate release APK
./gradlew assembleRelease

# Or generate App Bundle for Play Store
./gradlew bundleRelease
```

### Step 2: Sign APK

Setup signing in `build.gradle.kts`:
```kotlin
signingConfigs {
    create("release") {
        storeFile = file("path/to/keystore.jks")
        storePassword = "password"
        keyAlias = "alias"
        keyPassword = "password"
    }
}

buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
    }
}
```

### Step 3: Optimize for Release

The app includes ProGuard configuration (`proguard-rules.pro`) for:
- Code shrinking and obfuscation
- Logging removal
- Size optimization

### Step 4: Submit to Play Store

1. Create signed App Bundle via Android Studio
2. Go to Google Play Console
3. Create app listing
4. Upload bundle
5. Configure rollout percentage
6. Publish

## Performance Optimization Tips

1. **Pagination**: Activities use Paging 3 - loads only 10 items per page
2. **Image Loading**: Implement Glide for efficient image caching
3. **Token Refresh**: Implement background token refresh before expiry
4. **Caching**: Add Retrofit cache interceptor for offline support
5. **ProGuard**: Enabled in release builds for 30-40% size reduction

## Security Checklist

- [ ] Keycloak server uses HTTPS in production
- [ ] API_BASE_URL uses HTTPS
- [ ] Client secret is not hardcoded in production builds
- [ ] EncryptedSharedPreferences is used for token storage
- [ ] PKCE is enabled for OAuth2
- [ ] ProGuard obfuscation is enabled
- [ ] Sensitive logs are removed from release builds
- [ ] App uses certificate pinning (optional but recommended)

## Next Steps

1. Customize Material Design colors in `colors.xml`
2. Add app launcher icon in `src/main/res/mipmap/`
3. Implement offline support with Room database
4. Add unit and instrumentation tests
5. Implement analytics
6. Add crash reporting (Firebase Crashlytics)
7. Set up CI/CD pipeline (GitHub Actions, GitLab CI)
