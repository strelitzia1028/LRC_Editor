# LRC 歌词编辑器项目交接说明

本文档面向后续接手开发、构建和发布的人员，说明根目录中各个文件夹和文件的用途，以及非 Win7 原版与 Win7 支持版之间的关系。

## 项目概况

当前项目包含两个相互独立的版本：

| 版本 | 运行环境 | 源码目录 | 发布目录 |
| --- | --- | --- | --- |
| 非 Win7 原版 | JDK 17 + JavaFX 21 | `src/main` | `LRC Editor` |
| Win7 支持版 | Zulu JDK 8 + JavaFX 8 | `win7-support/src/main` | `LRC-Editor-Win7-x64` |

两个版本功能基本一致，但不是共享源码构建。修改共同功能时，需要同时检查并修改两套源码。

## 根目录文件

| 路径 | 作用 |
| --- | --- |
| `README.md` | 当前交接说明。 |
| `pom.xml` | 原版 Maven 配置，声明 JDK 17、JavaFX 21 和 jFLAC 依赖。 |
| `LRC Editor.iml` | IntelliJ IDEA 模块配置。 |
| `.idea/` | IntelliJ IDEA 项目配置。属于开发环境文件，不是软件运行依赖。 |
| `.git/` | Git 仓库元数据。 |
| `.gitignore` | Git 忽略规则。 |
| `src/` | 非 Win7 原版的正式源码和资源。 |
| `win7-support/` | Win7 支持版的独立源码、测试和构建脚本。 |
| `android/` | Android 原生移植工程，包含独立 Gradle 项目、测试和 APK 构建配置。 |
| `LRC Editor/` | 非 Win7 原版的已打包运行目录。 |
| `LRC-Editor-Win7-x64/` | Win7 支持版的已打包运行目录。 |
| `LRC-Editor-Win7-x64.zip` | Win7 支持版的分发压缩包。 |
| `LRC-Editor-Android-debug.apk` | 当前 Android 移植版的 Debug 签名安装包，适合侧载测试。 |
| `target/` | 编译、测试、打包和离线工具链目录。大部分是生成产物，不是源码。 |
| `LRC-Editor.ico` | 原版和 Win7 版打包时使用的多尺寸 Windows 图标。 |
| `LRC-Editor-icon.png` | 1024×1024 主图标原图。 |
| `LRC-Editor-icon-sizes/` | 从主图标导出的多种尺寸 PNG。 |
| `META-INF/` | 一次 JAR 操作留下的清单文件，不属于应用运行依赖。 |

## 非 Win7 原版源码

原版源码位于 `src/main`。

| 路径 | 作用 |
| --- | --- |
| `src/main/java/com/lrceditor/` | 原版全部 Java 源码。 |
| `src/main/resources/styles/` | 原版界面样式表 `app.css` 和深色主题 `dark-theme.css`。 |
| `src/main/resources/icons/` | 应用窗口图标。 |
| `src/test/` | 原版预留测试目录，目前主要测试程序在 `target/ui-check/`。 |

主要 Java 类：

| 文件 | 作用 |
| --- | --- |
| `LrcEditorApp.java` | 主窗口、菜单、播放、波形、主题、文件操作和界面入口。 |
| `LyricEditorView.java` | 歌词列表、单元格编辑、选择、高亮、快捷键和自动扩选。 |
| `LrcDocument.java` | 歌词文档模型、增删改、撤销重做、时间戳和偏移。 |
| `LrcIo.java` | LRC 文本解析、时间格式转换和文件写入。 |
| `LrcPasteParser.java` | 多行剪贴板解析、多时间戳识别和缺失时间戳补全。 |
| `LrcExportDialog.java` | 带保存路径、只读预览和时间戳合并/分离的导出窗口。 |
| `LyricLine.java` | 单行歌词数据模型。 |
| `AudioIo.java` | 音频读取与 FLAC 临时转换。 |
| `Waveform.java` | 波形数据提取。 |
| `CoverArtLoader.java` | 专辑封面和内嵌图片读取。 |
| `LyricVideoExporter.java` | 视频导出核心、ASS 字幕生成和 FFmpeg 调用。 |
| `VideoExportWindow.java` | 视频导出设置、预览、截取和背景效果窗口。 |
| `VideoTrimRangeControl.java` | 视频截取范围控件。 |

## Win7 支持版源码

Win7 版位于 `win7-support/`。它是从原版复制后做 Java 8 和 JavaFX 8 兼容改造的独立源码树。

| 路径 | 作用 |
| --- | --- |
| `win7-support/src/main/java/com/lrceditor/` | Win7 版 Java 8 源码。 |
| `win7-support/src/main/resources/styles/` | Win7 版样式表。内容和原版基本一致。 |
| `win7-support/src/main/resources/icons/` | Win7 版窗口图标。 |
| `win7-support/src/main/resources/help/` | 使用说明、快捷键和注意事项文本。Java 8 不支持文本块，因此独立为 UTF-8 资源。 |
| `win7-support/src/test/java/com/lrceditor/` | Win7 版 UI、音频、FLAC、视频导出和增删行滚动位置测试。 |
| `win7-support/package/` | 发布包内附带的 Win7 系统说明和第三方组件声明。 |
| `win7-support/README.md` | Win7 兼容改造的技术说明。 |
| `win7-support/build-win7.ps1` | Win7 版一键构建、测试和打包脚本。 |

Win7 版与原版的主要代码差异：

- Java 语法降级为 Java 8，例如移除 record、switch 表达式、模式匹配 `instanceof`、文本块和 `var`。
- JavaFX 21 API 替换为 JavaFX 8 可用写法。
- 使用 JDK 8 的 `javapackager` 生成自带 JRE 的应用镜像。
- `build-win7.ps1` 使用 `-nocss2bin` 保留文本 CSS。若 CSS 被转换成 `.bss`，JavaFX 8 会因代码仍读取 `.css` 而完全丢失样式。
- FFmpeg 使用 4.4.1，以保证 Windows 7 原生 API 兼容。
- `LyricVideoExporter.findFfmpeg()` 支持从便携包 `app/ffmpeg/ffmpeg.exe` 自动查找视频引擎。
- 歌词单元格监听实际 `selectedProperty()`，确保 JavaFX 8 下整行选中高亮刷新正确。

## Android 移植版

Android 版位于 `android/`。桌面版使用 JavaFX，不能直接在 Android 上运行，因此 Android 版使用原生 Android Java、平台控件、MediaPlayer 和 MediaCodec 重新实现界面与音频链路。

| 路径 | 作用 |
| --- | --- |
| `android/app/src/main/java/` | Android 原生 Java 源码。 |
| `android/app/src/main/res/` | Android 布局、颜色、主题、字符串和图标。 |
| `android/app/src/test/` | LRC 模型和编码的 JVM 单元测试。 |
| `android/gradlew.bat` | Gradle Wrapper 启动脚本。 |
| `android/README.md` | Android 架构、功能范围、构建和安装说明。 |
| `android/app/build/outputs/apk/debug/app-debug.apk` | 构建后的可安装 Debug APK。 |

Android 当前覆盖歌词编辑、文件导入保存、音频播放、波形定位、打点、歌词显示提前量、批量插入、多行粘贴、导出预览与时间戳合并、自动同组/扩选、撤销重做、明暗主题、播放组高亮和一次性自动选中同步。桌面版的滚动歌词视频生成尚未移植，属于下一阶段模块。

Android 构建：

```powershell
cd android
.\gradlew.bat testDebugUnitTest assembleDebug
```

## 原版发布目录

`LRC Editor/` 是非 Win7 原版的完整运行目录，由 JDK 17 `jpackage` 生成。

| 路径 | 作用 |
| --- | --- |
| `LRC Editor/LRC Editor.exe` | 原版启动器。 |
| `LRC Editor/app/LRC Editor.cfg` | 模块名、启动参数和 JVM 参数。 |
| `LRC Editor/app/ffmpeg/ffmpeg.exe` | 原版视频导出使用的 FFmpeg。 |
| `LRC Editor/runtime/` | 原版私有 Java 17 运行时，包含 JavaFX 21 和应用模块。 |

原版发布目录不要直接修改 Java 或样式文件。源码修改完成后，应重新编译、创建模块 JAR，再用 `jpackage` 重建镜像。

当前原版构建工作目录：

| 路径 | 作用 |
| --- | --- |
| `target/package-build/` | 早期原版模块化打包工作目录，包含 `module-info.java`、JavaFX 模块和 jFLAC 模块 JAR。 |
| `target/main-rebuild/` | 最近一次原版重新编译和 `jpackage` 构建目录，也是当前发布目录的来源。 |
| `target/main-scroll-verify/` | 原版增删行滚动位置回归测试的编译产物。 |
| `target/main-image-extract/` | 从最终运行时提取模块类后的校验目录。 |
| `target/LRC-Editor-main-backup/` | 替换原版发布目录前保留的上一版镜像备份。确认新版本稳定后可以删除。 |

## Win7 发布目录

`LRC-Editor-Win7-x64/` 是 Win7 版的完整便携目录，由 JDK 8 `javapackager` 生成。

| 路径 | 作用 |
| --- | --- |
| `LRC-Editor-Win7-x64/LRC Editor Win7.exe` | Win7 版启动器。 |
| `LRC-Editor-Win7-x64/app/LRC-Editor-Win7.jar` | Win7 版应用 JAR。 |
| `LRC-Editor-Win7-x64/app/jflac-codec-1.5.2.jar` | FLAC 解码依赖。 |
| `LRC-Editor-Win7-x64/app/LRC Editor Win7.cfg` | 主 JAR、依赖和 JRE 路径配置。 |
| `LRC-Editor-Win7-x64/app/ffmpeg/ffmpeg.exe` | Win7 兼容的 FFmpeg 4.4.1。 |
| `LRC-Editor-Win7-x64/runtime/` | 私有 Zulu JDK 8 与 JavaFX 8 运行时。 |
| `LRC-Editor-Win7-x64/README-Win7.txt` | 面向最终用户的 Win7 安装和故障说明。 |
| `LRC-Editor-Win7-x64/THIRD-PARTY-NOTICES.txt` | 第三方组件许可说明。 |

`LRC-Editor-Win7-x64.zip` 是上述目录的压缩包，可直接分发。必须完整解压后运行，不能只复制 exe。

Win7 系统建议：

- Windows 7 SP1 64 位。
- 安装 SHA-2 代码签名支持更新 `KB4474419`。
- 必要时安装服务堆栈更新 `KB4490628`。

## target 构建目录

`target/` 不是源码。大部分内容可重新生成，但其中也保存了离线构建工具链。

### 重要工具链

| 路径 | 作用 |
| --- | --- |
| `target/compile-libs/` | 原版 JavaFX 21 和 jFLAC 编译依赖。 |
| `target/jdk8-win7/` | Win7 版使用的 Zulu JDK 8u504 + JavaFX 8。重建 Win7 版需要。 |
| `target/ffmpeg-win7/` | Win7 版使用的 FFmpeg 4.4.1。重建 Win7 版需要。 |
| `target/android-tools/` | 本地 Android SDK、Build Tools 和 Gradle 8.9，用于构建安卓版。 |
| `target/ffmpeg-dist/` | 原版发布包使用的较新 FFmpeg。 |
| `target/package-build/module-src/module-info.java` | 原版模块声明，是原版 jpackage 构建的重要输入。 |
| `target/package-build/mods/jflac-codec-1.5.2-explicit.jar` | 带模块信息的 jFLAC JAR。 |

### Win7 构建与测试产物

| 路径 | 作用 |
| --- | --- |
| `target/win7-build/` | `build-win7.ps1` 当前使用的编译、测试、应用镜像和发布暂存目录。 |
| `target/win7-classes/` | Win7 版单独编译的 class 文件。 |
| `target/win7-test-classes/` | Win7 版测试 class 文件。 |
| `target/win7-scroll-tests/` | 增删行滚动位置测试的 class 文件。 |
| `target/win7-audit-classes/` | 主题和选中高亮审计测试的 class 文件。 |
| `target/win7-base/`、`win7-stage/`、`win7-deploy/` | Win7 早期打包步骤的中间目录。 |
| `target/win7-jarcss/` | 检查 JAR 内 CSS 资源时使用的临时目录。 |
| `target/win7-*-prefs/` | 隔离 Java Preferences 的测试目录，可删除。 |

### 原版和功能测试产物

| 路径 | 作用 |
| --- | --- |
| `target/classes/` | 原版历史编译输出。IntelliJ 或 Maven 也可能使用类似目录。 |
| `target/ui-check/` | 原版 UI、暗色主题、视频窗口、视频导出等功能测试源码和 class。 |
| `target/album-cover-smoke*` | 专辑封面视频导出测试结果。 |
| `target/video-*` | 视频滚动、质感、截断和背景效果测试输出。 |
| `target/dark-combined*` | 暗色与玻璃磨砂效果测试输出。 |
| `target/icon-build/` | 应用程序图标生成工具及编译产物。 |
| `target/generated-sources/`、`target/maven-status/` | Maven 或 IDE 生成的空目录和编译状态信息。 |
| `target/jflac-explicit/`、`target/jflac-module-classes/`、`target/jflac-module-gen/` | 为模块化打包准备 jFLAC `module-info` 时的转换工作目录。 |
| `target/modena-extract/` | 分析 JavaFX Modena 默认样式时使用的临时目录。 |
| `target/main-scroll-prefs/` | 原版回归测试使用的隔离 Java Preferences 目录。 |
| `target/win7-final-tests/`、`target/win7-final-prefs/` | Win7 发布前完整测试的 class 和隔离偏好目录。 |
| `target/win7-deploy/`、`target/win7-stage/`、`target/win7-base/` | Win7 早期打包容器的中间产物，不是当前正式发布源。 |

这些测试输出可以删除，不会影响源码，但可能影响复现历史测试结果。

## 开发流程

### 修改普通功能

1. 先在原版 `src/main` 中修改并验证。
2. 判断功能是否也属于 Win7 版。绝大多数编辑、播放、界面和视频功能需要同步。
3. 在 `win7-support/src/main` 中按 Java 8 和 JavaFX 8 的限制同步实现。
4. 分别运行原版和 Win7 版的编译和界面测试。
5. 重新生成两个发布目录。

两套源码没有自动共享机制，不要只修改其中一套后直接复制整个文件。Win7 版包含 Java 8 降级、资源路径和打包兼容差异，应针对差异逐段同步。

### 构建 Win7 版

在项目根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\win7-support\build-win7.ps1
```

脚本会执行以下工作：

1. 使用 `target/jdk8-win7` 中的 JDK 8 编译 Win7 源码。
2. 复制样式、图标和帮助文本资源。
3. 检查 JAR 中保留 `app.css` 和 `dark-theme.css`。
4. 使用 `javapackager` 生成带私有 JRE 的应用镜像。
5. 复制 jFLAC 和 FFmpeg 4.4.1。
6. 运行视频导出冒烟测试。
7. 生成根目录下的 `LRC-Editor-Win7-x64/` 和 ZIP。

### 构建原版

原版目前没有独立的一键构建脚本，使用 JDK 17 和 JavaFX 21 通过 `jpackage` 生成。

基本流程：

1. 使用 `D:\JDK17` 中的 `javac` 和 `target/compile-libs` 中的 JavaFX 21 依赖编译 `src/main/java`。
2. 将 `src/main/resources` 复制到 class 输出。
3. 结合 `target/package-build/module-src/module-info.java` 创建 `com.lrceditor.jar`。
4. 将 `com.lrceditor.jar`、JavaFX 模块 JAR 和 jFLAC 模块 JAR 放入同一 module path。
5. 使用 `jpackage --type app-image --module com.lrceditor/com.lrceditor.LrcEditorApp` 生成应用镜像。
6. 将 FFmpeg 复制到发布目录的 `app/ffmpeg/ffmpeg.exe`。
7. 启动测试后再替换根目录的 `LRC Editor/`。

`target/main-rebuild/` 可以作为最近一次正确构建的参考。

## 发布注意事项

- 不要直接把源码文件复制进 `LRC Editor/runtime/lib/modules` 或 Win7 的 `runtime/`。
- 不要把两套版本的发布目录混合使用。
- Win7 版必须使用 Java 8 编译，不能使用 JDK 17 生成的 class。
- Win7 打包必须保留文本 CSS，否则 JavaFX 8 会丢失全部自定义样式。
- 原版使用现代 JDK 和 JavaFX，不需要 Win7 的 Java 8 兼容代码。
- 发布前至少验证启动、深色/浅色主题、歌词选中、新增行、删除行、音频播放和视频导出。
- 用户设置保存在 Windows 用户级 Java Preferences 中，不在项目目录里。两个版本使用相同偏好节点，因此主题、上次目录等设置可能共用。

## 快速判断目录性质

| 是否需要长期保留 | 目录 |
| --- | --- |
| 必须保留 | `src/`、`win7-support/src/`、`android/`、两个构建脚本、`pom.xml`、图标文件 |
| 发布给用户 | `LRC Editor/`、`LRC-Editor-Win7-x64/`、`LRC-Editor-Win7-x64.zip` |
| 离线构建需要 | `target/jdk8-win7/`、`target/ffmpeg-win7/`、`target/compile-libs/`、`target/package-build/module-src/` |
| 可以重新生成或清理 | `target/classes/`、`target/ui-check/`、`target/video-*`、`target/win7-*` 中的测试和旧打包产物 |
