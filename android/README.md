# LRC Editor Android

这是非 Win7 桌面原版的 Android 原生移植工程。桌面版基于 JavaFX，无法直接运行在 Android 上，因此本工程使用 Android 原生 Java、平台控件和 MediaPlayer 重写界面与音频链路，同时沿用桌面版的 LRC 数据模型和编辑规则。

## 当前功能

- 导入 `.lrc`、`.txt` 歌词。
- 通过系统 Storage Access Framework 导入 MP3、WAV、FLAC 等音频，无需存储权限。
- 音频播放、暂停、前后 3 秒跳转、进度条拖动。
- 从波形图点击或拖动定位播放时间。
- 歌词行选择、二次点击编辑，以及独立的“编辑行”按钮。
- 输入时间戳并保存歌词文本。
- 使用当前播放时间为选中行批量打时间戳。
- 自定义歌词显示提前量（毫秒），不会修改 LRC 实际时间戳。
- 新增行、批量删除、撤销、重做。
- 自定义一次插入 1 到 1000 行。
- 从系统剪贴板粘贴多行歌词，自动识别多个时间戳，并支持无时间戳继承与默认 0。
- 自动选择相同时间戳歌词。
- 自动向下扩选 0 到 10 行。
- 两种自动选择功能互斥，并显示冲突确认提示。
- 深色和浅色主题，主题设置持久化。
- 当前播放行颜色可选黑、白、红、绿、蓝。
- 三行歌词预览、当前行加粗放大。
- 专辑封面读取和唱片旋转动画。
- 音频波形分析、始终跟随播放位置的黄色播放组高亮，以及仅在新歌词组出现时同步一次的自动选中。
- 保存回已打开的歌词文件，或通过系统文件选择器另存、导出。
- 导出前显示只读可复制预览，可切换相同歌词内容的时间戳合并/分离。
- 主题切换和 Activity 重建时恢复当前歌词、文件 URI 和播放状态。

## 当前不包含

桌面版的滚动歌词视频生成尚未移植到 Android。视频导出涉及 Android MediaCodec、MediaMuxer、音频重编码、背景视频、ASS 字幕和质感滤镜，建议作为下一阶段的独立模块实现。当前 Android 版不提供伪造的视频导出入口。

## 目录

| 路径 | 作用 |
| --- | --- |
| `app/src/main/java/com/lrceditor/mobile/MainActivity.java` | 主界面、文件操作、播放、编辑和所有交互。 |
| `app/src/main/java/com/lrceditor/mobile/model/` | LRC 模型、解析、序列化、撤销重做和打点逻辑。 |
| `app/src/main/java/com/lrceditor/mobile/audio/` | MediaPlayer 播放封装和媒体波形解码。 |
| `app/src/main/java/com/lrceditor/mobile/ui/` | 歌词适配器和主题调色板。 |
| `app/src/main/java/com/lrceditor/mobile/view/` | 唱片绘制和波形控件。 |
| `app/src/main/res/layout/activity_main.xml` | 单 Activity 主界面布局。 |
| `app/src/main/res/values/` | 中文字符串、颜色和明暗主题。 |
| `app/src/test/java/` | LRC 解析、编辑、撤销、分组和编码测试。 |
| `gradlew.bat` | Windows 下无需安装 Gradle 的构建入口。 |

## 构建

要求：

- JDK 17。
- Android SDK Platform 35。
- Android Build Tools 35.0.0。

项目默认从 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT` 查找 SDK。也可以创建不提交到 Git 的 `local.properties`：

```properties
sdk.dir=E:\\path\\to\\android-sdk
```

Windows 构建：

```powershell
cd android
.\gradlew.bat testDebugUnitTest assembleDebug
```

Debug APK：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

安装到已连接设备：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

当前 APK 使用 Android 调试证书签名，适合本机测试和侧载。正式发布到应用商店前，应创建并妥善保管自己的发布证书，再生成 signed release APK 或 AAB。

## 与桌面版的同步

Android 版不是桌面 Java 源码的直接共享模块。修改以下规则时，应同时检查桌面原版、Win7 版和 Android 版：

- LRC 时间戳解析和序列化。
- 打点、偏移、时间分块和撤销重做。
- 自动选择同组、自动扩选及互斥规则。
- 主题与当前播放行颜色。
- 新增行、删除行后保持列表位置。
- 播放高亮、三行预览和专辑封面。

Android 特有约束：

- 文件访问使用 `content://` URI，不假设歌词或音频具有普通文件路径。
- 音频通过 Android MediaPlayer 播放，FLAC 由系统解码器处理。
- 大 FLAC 的波形分析会显示百分比进度；播放不依赖波形分析完成。
- 波形由 MediaExtractor 和 MediaCodec 解码 PCM 后生成。
- `targetSdk 35` 强制 edge-to-edge，所以主界面必须应用系统栏 Insets。
- 活动主题切换会触发 Activity 重建，歌词和 URI 状态必须通过 `onSaveInstanceState` 保存。
