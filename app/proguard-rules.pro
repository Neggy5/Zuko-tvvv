# ══════════════════════════════════════════════════════════
# ZUKO TV — Release hardening (R8)
# ══════════════════════════════════════════════════════════

# Optimization
-optimizationpasses 5
-allowaccessmodification
-repackageclasses 'z'
-overloadaggressively

# Strip source file / line numbers from stack traces in production
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Remove Log calls (reduces string leakage)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# Kotlin / serialization (required to not break network models)
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**
-keep @kotlinx.serialization.Serializable class com.zuko.tv.data.** { *; }

# Media3
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }
-keepclassmembers class * extends androidx.media3.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Coil3 (network image loading — must not be stripped by R8)
-keep class coil3.** { *; }
-keep interface coil3.** { *; }
-dontwarn coil3.**
-dontwarn coil.**


# Compose / Kotlin metadata needed at runtime
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-dontwarn kotlin.**

# Keep Application entry + Activity names (manifest)
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity

# Obfuscate our app package aggressively — only keep entry points
-keep class com.zuko.tv.MainActivity { public <init>(); }
-keep class com.zuko.tv.ZukoApplication { public <init>(); }

# Security helper — keep class name short via repackage, allow full obfuscation of members
-keepclassmembers class com.zuko.tv.security.IntegrityGuard {
    public static *** check(...);
}

# Do not warn on missing optional APIs
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
