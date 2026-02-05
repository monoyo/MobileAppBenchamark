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

# AndroidX Lifecycle (for LiveData/ViewModel if used)
-keep class androidx.lifecycle.** { *; }

# Keep model classes used in benchmarks to prevent stripping
-keep class com.jossy.android.mobilebenchmarkappkotlin.model.** { *; }
-keep class com.jossy.android.mobilebenchmarkappjava.data.** { *; }
