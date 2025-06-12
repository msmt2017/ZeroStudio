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

# --- New Fixes for Missing Classes ---
# Keep the BaseApplication class specifically referenced by TermuxApplication
-keep class com.itsaky.androidide.app.BaseApplication { *; }

# Suppress warnings for StringConcatFactory, which is often a compatibility issue with Java 9+ features
# and older Android platforms, or a warning that can be safely ignored as it's a platform feature.
-dontwarn java.lang.invoke.StringConcatFactory
# --- End New Fixes ---


# Keep standard Java/Android APIs
-keep class javax.** { *; }
-keep class jdkx.** { *; }

# Keep javac classes
-keep class openjdk.** { *; }

# Eclipse related classes
-keep class org.eclipse.** { *; }

# JAXP (Java API for XML Processing) related classes
-keep class jaxp.** { *; }
-keep class org.w3c.** { *; }
-keep class org.xml.** { *; }

# Services (e.g., using com.google.auto.service.AutoService)
-keep @com.google.auto.service.AutoService class ** {
}
-keepclassmembers class ** {
    @com.google.auto.service.AutoService <methods>;
}

# EventBus (org.greenrobot.eventbus)
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }
-keepclassmembers class * extends org.greenrobot.eventbus.util.ThrowableFailureEvent {
    <init>(java.lang.Throwable);
}

# Classes accessed reflectively or dynamically
-keep class io.github.rosemoe.sora.widget.component.EditorAutoCompletion {
    io.github.rosemoe.sora.widget.component.EditorCompletionAdapter adapter;
    int currentSelection;
}
-keep class com.itsaky.androidide.projects.util.StringSearch {
    packageName(java.nio.file.Path);
}
-keep class * implements org.antlr.v4.runtime.Lexer {
    <init>(...);
}
-keep class * extends com.itsaky.androidide.lsp.java.providers.completion.IJavaCompletionProvider {
    <init>(...);
}
-keep class com.itsaky.androidide.editor.api.IEditor { *; }
-keep class * extends com.itsaky.androidide.inflater.IViewAdapter { *; }
-keep class * extends com.itsaky.androidide.inflater.drawable.IDrawableParser {
    <init>(...);
    android.graphics.drawable.Drawable parse();
    android.graphics.drawable.Drawable parseDrawable();
}
-keep class com.itsaky.androidide.utils.DialogUtils { public <methods>; }

# APK Metadata models
-keep class com.itsaky.androidide.models.ApkMetadata { *; }
-keep class com.itsaky.androidide.models.ArtifactType { *; }
-keep class com.itsaky.androidide.models.MetadataElement { *; }

# Parcelable implementations (for Android Parcelable objects)
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Enum classes used in preferences or other specific contexts
-keep enum org.eclipse.lemminx.dom.builder.EmptyElements { *; }
-keep enum com.itsaky.androidide.xml.permissions.Permission { *; }

# Tree-sitter library (native methods and fields)
-keepclasseswithmembers class ** {
    native <methods>;
}
-keep class com.itsaky.androidide.treesitter.** { *; }

# Retrofit 2 (HTTP client)
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp3 (HTTP client)
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Stat uploader classes
-keep class com.itsaky.androidide.stats.** { *; }

# Gson (JSON serialization/deserialization library)
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
## Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

## Themes enum
-keep enum com.itsaky.androidide.ui.themes.IDETheme {
    *;
}

## Contributor models - deserialized with GSON
-keep class * implements com.itsaky.androidide.contributors.Contributor {
    *;
}

# Suppress missing class warnings for specific libraries/APIs not strictly needed at runtime
## These are used in annotation processing process in the Java Compiler
-dontwarn sun.reflect.annotation.AnnotationParser
-dontwarn sun.reflect.annotation.AnnotationType
-dontwarn sun.reflect.annotation.EnumConstantNotPresentExceptionProxy
-dontwarn sun.reflect.annotation.ExceptionProxy

## Used in Logback. We do not need this though.
-dontwarn jakarta.servlet.ServletContainerInitializer

## These are used in JGit. TODO(itsaky): Verify if it is safe to ignore these warnings
-dontwarn java.lang.ProcessHandle
-dontwarn java.lang.management.ManagementFactory
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid

# Keep service provider configuration files
-keepdirectories META-INF/services
# -keep resource metaservices.properties # Uncomment if you specifically need this resource
