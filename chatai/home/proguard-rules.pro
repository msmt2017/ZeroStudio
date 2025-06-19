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
# --- End Application Specific Package Keeping ---
# Force application entry points and their direct dependencies into the primary DEX file.

-keep class com.itsaky.androidide.app.IDEApplication { *; }
-keep class me.rerere.rikkahub.RikkaHubApp { *; }
-keep class com.termux.app.TermuxApplication { *; }
-keep class com.itsaky.androidide.app.BaseApplication { *; }

-keep class * extends android.app.Application { *; }

-keep class * extends androidx.multidex.MultiDexApplication { *; }

-keep class org.koin.** { *; }

# Keep service provider configuration files
-keepdirectories META-INF/services
# -keep resource metaservices.properties 