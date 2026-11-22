# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Production R8 / ProGuard keep rules for SOGO CHAT AND MEET
-keep class * {
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <methods>;
}
-keep @androidx.annotation.Keep class * { *; }

# Keep Firestore POJO models & PropertyName annotations
-keepclassmembers class com.example.data.model.** { *; }
-keepattributes *Annotation*,Signature

# WebRTC native signaling classes
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Networking & Moshi
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
-dontwarn okhttp3.**
-dontwarn retrofit2.**
