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

# Keep service provider configuration files
-keepdirectories META-INF/services
# -keep resource metaservices.properties 

