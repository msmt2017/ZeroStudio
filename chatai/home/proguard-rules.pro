
-dontobfuscate

-keepclassmembers class com.itsaky.androidide.**  { *;}
-keepclassmembers class com.termux.**  { *;}
-keepclassmembers class me.rerere.**  { *;}

-keepclassmembers class me.rerere.rikkahub.RikkaHubApp.** { *;}

-keepclassmembers class com.termux.app.TermuxApplication.**  { *;}


-keep class com.termux.app.** { *; }
-keep class com.itsaky.androidide.app.** { *; }

# 确保 Koin 相关的类不被混淆或错误优化，因为你的 onCreate 中有 Koin 初始化
-keep class org.koin.** { *; }
-dontwarn org.koin.**