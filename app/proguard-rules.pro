# mpv JNI bridge — must not be shrunk
-keep class is.xyz.mpv.** { *; }
-keepclasseswithmembernames class * {
native <methods>;
}
# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
# Jetpack Compose
-keep class androidx.compose.** { ; }
-dontwarn androidx.compose.*
