# ======================
# 基础保留规则（强化）
# ======================
-keepattributes *Annotation*,KotlinMetadata,Signature
-keepclassmembers class * {
    @androidx.annotation.NonNull <methods>;
    @androidx.annotation.Nullable <methods>;
}

# ======================
# 保留所有 Lambda 依赖的接口（含方法）
# ======================
-keep interface com.termux.shared.termux.interact.TextInputDialogUtils$TextSetListener.**  { *; }

# ======================
# 保留 AndroidIDE 整个包（之前仅保留单个类）
# ======================
-keep class com.itsaky.androidide.app.** { *; }

# ======================
# 保留 termux.shared 模块（含内部类、枚举、接口）
# ======================
-keep class com.termux.shared.** { *; }
-keep interface com.termux.shared.** { *; }
-keepclassmembers class com.termux.shared.termux.extrakeys.ExtraKeysConstants.**  { *; }


-keepclassmembers class com.termux.shared.termux.extrakeys.ExtraKeysConstants$ExtraKeyDisplayMap { *; }

# ======================
# 保留 termux.terminal 模块（含所有类和接口）
# ======================
-keep class com.termux.terminal.** { *; }
-keep interface com.termux.terminal.** { *; }

# ======================
# 保留所有 Activity/Service 及其内部类
# ======================
-keep public class * extends android.app.Activity {
    public <init>(android.content.Context);
    public void *(android.view.View);
}
-keep public class * extends android.app.Service { *; }

# ======================
# 关闭所有 R8 优化（临时方案，确保类不被误删）
# ======================
-dontoptimize
-dontpreverify
-optimizationpasses 1

# ======================
# 从 missing_rules.txt 转换的完整保留规则（关键）
# ======================
-keep class com.termux.shared.activities.ReportActivity { *; }
-keep class com.termux.shared.activity.ActivityUtils { *; }
-keep class com.termux.shared.activity.media.AppCompatActivityUtils { *; }
-keep class com.termux.shared.android.AndroidUtils { *; }
-keep class com.termux.shared.android.PackageUtils { *; }
-keep class com.termux.shared.android.PermissionUtils { *; }
-keep class com.termux.shared.data.DataUtils { *; }
-keep class com.termux.shared.data.IntentUtils { *; }
-keep class com.termux.shared.errors.Errno { *; }
-keep class com.termux.shared.errors.Error { *; }
-keep class com.termux.shared.file.FileUtils { *; }
-keep class com.termux.shared.interact.MessageDialogUtils { *; }
-keep class com.termux.shared.interact.ShareUtils { *; }
-keep class com.termux.shared.models.ReportInfo { *; }
-keep class com.termux.shared.net.uri.UriUtils { *; }
-keep class com.termux.shared.notification.NotificationUtils { *; }
-keep class com.termux.shared.shell.ShellUtils { *; }
-keep class com.termux.shared.shell.command.ExecutionCommand$Runner { *; }
-keep class com.termux.shared.shell.command.ExecutionCommand$ShellCreateMode { *; }
-keep class com.termux.shared.shell.command.ExecutionCommand { *; }
-keep interface com.termux.shared.shell.command.environment.IShellEnvironment { *; }
-keep class com.termux.shared.shell.command.result.ResultConfig { *; }
-keep class com.termux.shared.shell.command.runner.app.AppShell$AppShellClient { *; }
-keep class com.termux.shared.shell.command.runner.app.AppShell { *; }
-keep class com.termux.shared.termux.TermuxBootstrap { *; }
-keep class com.termux.shared.termux.TermuxConstants { *; }
-keep class com.termux.shared.termux.TermuxUtils { *; }
-keep class com.termux.shared.termux.crash.TermuxCrashUtils { *; }
-keep class com.termux.shared.termux.data.TermuxUrlUtils { *; }
-keep class com.termux.shared.termux.extrakeys.ExtraKeyButton { *; }
-keep class com.termux.shared.termux.extrakeys.ExtraKeysInfo { *; }
-keep interface com.termux.shared.termux.extrakeys.ExtraKeysView$IExtraKeysView { *; }
-keep class com.termux.shared.termux.extrakeys.ExtraKeysView { *; }
-keep class com.termux.shared.termux.extrakeys.SpecialButton { *; }
-keep class com.termux.shared.termux.file.TermuxFileUtils { *; }
-keep class com.termux.shared.termux.interact.TextInputDialogUtils { *; }
-keep class com.termux.shared.termux.plugins.TermuxPluginUtils { *; }
-keep class com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences { *; }
-keep class com.termux.shared.termux.settings.properties.TermuxAppSharedProperties { *; }
-keep class com.termux.shared.termux.settings.properties.TermuxPropertyConstants { *; }
-keep class com.termux.shared.termux.shell.TermuxShellManager { *; }
-keep class com.termux.shared.termux.shell.TermuxShellUtils { *; }
-keep class com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment { *; }
-keep class com.termux.shared.termux.shell.command.runner.terminal.TermuxSession$TermuxSessionClient { *; }
-keep class com.termux.shared.termux.shell.command.runner.terminal.TermuxSession { *; }
-keep class com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase { *; }
-keep class com.termux.shared.termux.terminal.TermuxTerminalViewClientBase { *; }
-keep class com.termux.shared.termux.terminal.io.BellHandler { *; }
-keep class com.termux.shared.termux.terminal.io.TerminalExtraKeys { *; }
-keep class com.termux.shared.termux.theme.TermuxThemeUtils { *; }
-keep class com.termux.shared.theme.NightMode { *; }
-keep class com.termux.shared.theme.ThemeUtils { *; }
-keep class com.termux.shared.view.KeyboardUtils { *; }
-keep class com.termux.shared.view.ViewUtils { *; }
-keep class com.termux.terminal.KeyHandler { *; }
-keep class com.termux.terminal.TerminalBuffer { *; }
-keep class com.termux.terminal.TerminalColorScheme { *; }
-keep class com.termux.terminal.TerminalColors { *; }
-keep class com.termux.terminal.TerminalEmulator { *; }
-keep class com.termux.terminal.TerminalSession { *; }
-keep interface com.termux.terminal.TerminalSessionClient { *; }
-keep class com.termux.view.TerminalView { *; }
-keep class com.termux.view.TerminalViewClient { *; }
