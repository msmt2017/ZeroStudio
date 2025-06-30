# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep class com.itsaky.androidide.**

-dontwarn com.itsaky.androidide.eventbus.events.EventReceiver
-dontwarn com.itsaky.androidide.eventbus.events.editor.DocumentSaveEvent
-dontwarn com.itsaky.androidide.eventbus.events.file.FileCreationEvent
-dontwarn com.itsaky.androidide.eventbus.events.file.FileDeletionEvent
-dontwarn com.itsaky.androidide.eventbus.events.file.FileRenameEvent
-dontwarn com.itsaky.androidide.xml.resources.ResourceTableRegistry$Companion
-dontwarn com.itsaky.androidide.xml.resources.ResourceTableRegistry
-dontwarn com.itsaky.androidide.xml.versions.ApiVersions
-dontwarn com.itsaky.androidide.xml.versions.ApiVersionsRegistry$Companion
-dontwarn com.itsaky.androidide.xml.versions.ApiVersionsRegistry
-dontwarn com.itsaky.androidide.xml.widgets.WidgetTable
-dontwarn com.itsaky.androidide.xml.widgets.WidgetTableRegistry$Companion
-dontwarn com.itsaky.androidide.xml.widgets.WidgetTableRegistry
-dontwarn java.lang.invoke.StringConcatFactory