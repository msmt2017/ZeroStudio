# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-dontobfuscate
#-renamesourcefileattribute SourceFile
#-keepattributes SourceFile,LineNumberTable

# Temp fix for androidx.window:window:1.0.0-alpha09 imported by termux-shared
# https://issuetracker.google.com/issues/189001730
# https://android-review.googlesource.com/c/platform/frameworks/support/+/1757630
-keep class androidx.window.** { *; }

-dontwarn com.itsaky.androidide.app.BaseIDEActivity
-dontwarn com.termux.shared.activity.ActivityUtils
-dontwarn com.termux.shared.android.PackageUtils
-dontwarn com.termux.shared.android.PermissionUtils
-dontwarn com.termux.shared.data.DataUtils
-dontwarn com.termux.shared.data.IntentUtils
-dontwarn com.termux.shared.errors.Errno
-dontwarn com.termux.shared.errors.Error
-dontwarn com.termux.shared.file.FileUtils
-dontwarn com.termux.shared.interact.MessageDialogUtils
-dontwarn com.termux.shared.net.uri.UriUtils
-dontwarn com.termux.shared.notification.NotificationUtils
-dontwarn com.termux.shared.shell.ShellUtils
-dontwarn com.termux.shared.shell.command.ExecutionCommand$Runner
-dontwarn com.termux.shared.shell.command.ExecutionCommand$ShellCreateMode
-dontwarn com.termux.shared.shell.command.ExecutionCommand
-dontwarn com.termux.shared.shell.command.environment.IShellEnvironment
-dontwarn com.termux.shared.shell.command.result.ResultConfig
-dontwarn com.termux.shared.shell.command.runner.app.AppShell$AppShellClient
-dontwarn com.termux.shared.shell.command.runner.app.AppShell
-dontwarn com.termux.shared.termux.TermuxConstants
-dontwarn com.termux.shared.termux.TermuxUtils$AppInfoMode
-dontwarn com.termux.shared.termux.TermuxUtils
-dontwarn com.termux.shared.termux.crash.TermuxCrashUtils
-dontwarn com.termux.shared.termux.file.TermuxFileUtils
-dontwarn com.termux.shared.termux.interact.TextInputDialogUtils$TextSetListener
-dontwarn com.termux.shared.termux.interact.TextInputDialogUtils
-dontwarn com.termux.shared.termux.plugins.TermuxPluginUtils
-dontwarn com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
-dontwarn com.termux.shared.termux.settings.properties.TermuxAppSharedProperties
-dontwarn com.termux.shared.termux.shell.TermuxShellManager
-dontwarn com.termux.shared.termux.shell.TermuxShellUtils
-dontwarn com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
-dontwarn com.termux.shared.termux.shell.command.runner.terminal.TermuxSession$TermuxSessionClient
-dontwarn com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
-dontwarn com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
-dontwarn com.termux.shared.theme.NightMode
-dontwarn com.termux.shared.theme.ThemeUtils
-dontwarn com.termux.shared.view.ViewUtils
-dontwarn com.termux.terminal.TerminalSession
-dontwarn com.termux.terminal.TerminalSessionClient