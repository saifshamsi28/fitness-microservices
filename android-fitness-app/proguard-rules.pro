# Retrofit
-keep class com.saif.fitnessapp.network.** { *; }
-keep interface com.saif.fitnessapp.network.** { *; }

# Gson
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

# DTOs
-keep class com.saif.fitnessapp.network.dto.** { *; }

# AppAuth
-keep class net.openid.appauth.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keep @dagger.hilt.android.HiltAndroidApp class *

# EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }

# Paging
-keep class androidx.paging.** { *; }

# Retrofit + Gson
-keepclasseswithmembers class * {
    @retrofit2.http.<methods> <methods>;
}

-keepclasseswithmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
