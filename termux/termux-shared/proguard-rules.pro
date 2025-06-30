# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-dontobfuscate
#-renamesourcefileattribute SourceFile
#-keepattributes SourceFile,LineNumberTable
-keep class com.itsaky.androidide.**
-keep class com.termux.**

# Keep the specific static method setDefaultLogTag in Logger class
-keepclassmembers class com.termux.shared.logger.Logger {
    public static void setDefaultLogTag(java.lang.String);
}

# Alternatively, you can keep the entire Logger class if you suspect other issues
# This is broader and might increase app size slightly, but safer if specific methods are hard to pinpoint.
-keep class com.termux.shared.logger.Logger { *; }

# If TermuxApplication or any related classes are also being affected by obfuscation/shrinking,
# you might need to keep them as well.
-keep class com.termux.app.** { *; }
-keep class com.termux.shared.** { *; }
-keepclassmembers class com.termux.** { *; }

# Suppress warnings related to these classes if they appear, as they might indicate R8 is seeing issues
-dontwarn com.termux.shared.logger.Logger
-dontwarn com.termux.app.**
-dontwarn com.termux.shared.**