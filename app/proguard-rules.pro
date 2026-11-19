# NeuroFlow ProGuard rules
-keep class com.neuroflow.app.service.** { *; }
-keepclassmembers class * extends android.accessibilityservice.AccessibilityService { *; }
-dontwarn kotlinx.coroutines.**
