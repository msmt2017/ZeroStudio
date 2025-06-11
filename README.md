<p align="center">
  <img src="./images/icon.png" alt="AndroidIDE" width="80" height="80"/>
</p>

<h2 align="center"><b>AndroidIDE</b></h2>
<p align="center">
  An IDE to develop real, Gradle-based Android applications on Android devices.
<p><br>

<p align="center">
<!-- Latest release -->
<img src="https://img.shields.io/github/v/release/msmt2017/ZeroStudio?include_prereleases&amp;label=latest%20release" alt="Latest release">
<!-- Build and test -->
<img src="https://github.com/msmt2017/ZeroStudio/actions/workflows/build.yml/badge.svg" alt="Builds and tests">
<!-- CodeFactor -->
<img src="https://www.codefactor.io/repository/github/msmt2017/ZeroStudio/badge/main" alt="CodeFactor">
<!-- Crowdin -->
<a href="https://crowdin.com/project/androidide"><img src="https://badges.crowdin.net/androidide/localized.svg" alt="Crowdin"></a>
<!-- License -->
<img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License"></p>

> **We are looking for contributors/maintainers to help keep the project alive and speed up the development. You can help by fixing bugs, implementing & improving features, writing documentation, etc.**

## Features

- [x] Gradle support.
- [x] `JDK 11` and `JDK 17` available for use.
- [x] Terminal with necessary packages.
- [x] Custom environment variables (for Build & Terminal).
- [x] SDK Manager (Available via terminal).
- [x] API information for classes and their members (since, removed, deprecated).
- [x] Log reader (shows your app's logs in real-time)
- [ ] Language servers
    - [x] Java
    - [x] XML
    - [ ] Kotlin
- [ ] UI Designer
    - [x] Layout inflater
    - [x] Resolve resource references
    - [x] Auto-complete resource values when user edits attributes using the attribute editor
    - [x] Drag & Drop
    - [x] Visual attribute editor
    - [x] Android Widgets
- [ ] String Translator
- [ ] Asset Studio (Drawable & Icon Maker)
- [x] Git
- # chat ai  [x] 可以与AI快捷聊天与编写代码等，也可以使用自己的mcp服务（You can chat and write code quickly with AI, and also use your own MCP service）

> _Please install AndroidIDE from trusted sources only i.e._

> - [_GitHub Releases_](https://github.com/msmt2017/ZeroStudio/releases)
> - [_GitHub Actions_](https://github.com/msmt2017/ZeroStudio/actions?query=branch%3Adev+event%3Apush)

- Download the AndroidIDE APK from the mentioned trusted sources.
- Follow the
  instructions [here](https://docs.androidide.com/tutorials/get-started.html) to
  install the build tools.

## Limitations

- For working with projects in AndroidIDE, your project must use Android Gradle Plugin v7.2.0 or
  newer. Projects with older AGP must be migrated to newer versions.
- SDK Manager is already included in Android SDK and is accessible in AndroidIDE via its Terminal.
  But, you cannot use it to install some tools (like NDK) because those tools are not built for
  Android.
- No official NDK support because we haven't built the NDK for Android.

The app is still being developed actively. It's in beta stage and may not be stable. if you have any
issues using the app, please let us know.


## Thanks to

- [Rosemoe](https://github.com/Rosemoe) for the
  awesome [CodeEditor](https://github.com/Rosemoe/sora-editor)
- [Termux](https://github.com/termux) for [Terminal Emulator](https://github.com/termux/termux-app)
- [Bogdan Melnychuk](https://github.com/bmelnychuk)
  for [AndroidTreeView](https://github.com/bmelnychuk/AndroidTreeView)
- [George Fraser](https://github.com/georgewfraser) for
  the [Java Language Server](https://github.com/georgewfraser/java-language-server)
- [rikkahub](https://github.com/rikkahub/rikkahub)
Thanks to all the developers who have contributed to this project.



## License

```
新协议条例：本质上是开源的，也是上游拉取下来的分支，不论上下游协议条例是什么，
本项目[msmt2017/ZeroStudio](https://github.com/msmt2017/ZeroStudio)里面的任何功能，
任何一个代码，任何一个文件及任何一个模块都不得用于任何行业及任何商业/付费/卡密/VIP/Pro与普通/支付解锁/付费解锁/看广告/植入广告或其它来达到收入的来源功能限制与影响等一切行为/行径（New protocol regulations: Essentially open source and a branch pulled down from upstream, regardless of what the upstream and downstream protocol regulations are,
This project [mmmt2017/ZeroStudio]（ https://github.com/msmt2017/ZeroStudio ）Any function inside,
Any code, file, or module shall not be used for any industry or any commercial/paid/encrypted/VIP/Pro or ordinary/payment unlock/payment unlock/viewing ads/embedding ads or any other source of income. Any behavior/path that restricts or affects the functionality of the source of income shall not be allowed）

此外特别提示：在本IDE也就是[msmt2017/ZeroStudio](https://github.com/msmt2017/ZeroStudio)里面制作/开发的然后项目任何代码与[msmt2017/ZeroStudio]无关，
包含使用本IDE内开发者开发的一切木马/病毒/或其它危害计算机及其它设备的程序应用软件等。
使用msmt2017/ZeroStudioIDE开发的内容请遵守当地或国家法律法规制度

In addition, a special note: in this IDE, which is [mmmt2017/ZeroStudio]（ https://github.com/msmt2017/ZeroStudio ）Any code produced/developed inside the project is unrelated to [smmt2017/ZeroStudio],
This includes all Trojan horses/viruses/or other program applications and software developed by the developer that pose a threat to computers and devices.
Please comply with local or national laws and regulations when developing content using msmt2017/ZeroStudioIDE



AndroidIDE is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

AndroidIDE is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
```

Any violations to the license can be reported either by opening an issue or writing a mail to us
directly.


#by android_zero/零丶