# LibGDX
-keep class com.badlogic.gdx.** { *; }
-dontwarn com.badlogic.gdx.**

# Kotlinx Serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Koin
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Keep our model classes for serialization
-keep class com.debtsdecks.core.** { *; }

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable