# General ProGuard/R8 configurations
-ignorewarnings
-dontwarn **
-dontnote **
-dontobfuscate

# --- Application Specific Package Keeping ---
# MANDATORY: Keep all classes and members in the specified packages
# This ensures that these core application and library components are not stripped or obfuscated.
-keep class me.rerere.rikkahub.** { *; }
-keep class com.itsaky.androidide.** { *; }
-keep class com.termux.** { *; }

-keepnames class com.itsaky.androidide.**
-keepnames class me.rerere.rikkahub.**
-keepnames class com.termux.**

-keep class com.itsaky.androidide.app.IDEApplication { *; }
-keep class me.rerere.rikkahub.RikkaHubApp { *; }
-keep class com.termux.app.TermuxApplication { *; }
-keep class com.itsaky.androidide.app.BaseApplication { *; }

-keep class * extends android.app.Application { *; }

-keep class org.koin.** { *; }

# Keep service provider configuration files
-keepdirectories META-INF/services
# -keep resource metaservices.properties 