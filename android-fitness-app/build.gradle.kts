plugins {
    id("com.android.application") version "8.2.0"
}

android {
    namespace = "com.saif.fitnessapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.saif.fitnessapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Core Android
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.11.0")

    // Architecture Components
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.7.0")
    implementation("androidx.activity:activity:1.8.0")
    implementation("androidx.fragment:fragment:1.6.2")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.50")
    annotationProcessor("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-lifecycle-viewmodel:1.0.0-alpha03")
    annotationProcessor("androidx.hilt:hilt-compiler:1.0.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.10.0")
    implementation("com.squareup.retrofit2:converter-gson:2.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Security & Storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Pagination
    implementation("androidx.paging:paging-runtime:3.2.1")

    // OAuth2 with AppAuth
    implementation("net.openid:appauth:0.11.0")

    // Chrome Custom Tabs
    implementation("androidx.browser:browser:1.7.0")

    // Shimmer Loading
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    // Image Loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
