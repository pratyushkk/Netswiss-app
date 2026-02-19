# ProGuard / R8 Rules for NetSwiss

# Keep application class
-keep class com.netswiss.app.** { *; }

# Keep Compose runtime
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# Keep Material3
-keep class androidx.compose.material3.** { *; }

# OSMDroid
-dontwarn org.osmdroid.**
-keep class org.osmdroid.** { *; }

# Play Services Location
-keep class com.google.android.gms.** { *; }

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# Don't break Kotlin
-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# General safe optimizations
-dontusemixedcaseclassnames
-repackageclasses ''
-allowaccessmodification
-dontnote **
