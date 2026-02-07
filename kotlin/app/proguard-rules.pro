# Kotlin Serialization
-keepattributes *Annotation*
-keepclassmembers class ** {
    @org.jetbrains.kotlinx.serialization.Serializable *;
}
-keepclassmembers class **$serializer {
    public static ** INSTANCE;
}

# Ktor
-keepattributes Signature
-keepattributes *Annotation*
-keep class io.ktor.** { *; }
-dontwarn java.lang.management.**
-dontwarn kotlinx.coroutines.debug.DebugProbesImpl

# SLF4J
-dontwarn org.slf4j.impl.**

# Coil
-keep class coil.** { *; }

# AndroidX Lifecycle
-keep class androidx.lifecycle.** { *; }

# Keep all benchmark classes to prevent stripping and ensure performance consistency
-keep class com.jossy.android.mobilebenchmarkappjava.** { *; }
-keep class com.jossy.android.mobilebenchmarkappkotlin.** { *; }

# Gson requirements
-keepattributes Signature, EnclosingMethod, InnerClasses
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-dontwarn com.google.gson.internal.bind.util.ISO8601Utils

# Keep model classes used for JSON serialization
-keepclassmembers class com.jossy.android.mobilebenchmarkappjava.data.** { <fields>; }
