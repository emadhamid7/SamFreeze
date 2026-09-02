# Freeze Manager release ProGuard rules
-keep class com.samfreeze.app.model.** { *; }
-keepattributes *Annotation*

# Freeze Manager release ProGuard rules
-keep class com.samfreeze.app.model.** { *; }
-keepattributes *Annotation*

-keep class androidx.lifecycle.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.activity.** { *; }
-dontwarn androidx.lifecycle.**

-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}