# EchoStep Android（Java 17 + ncnn Piper）

桌面版 EchoStep 的原生 Android 版本。Android 原生 View，Java 17，无 AndroidX、无 Compose。

> 当前版本已同步 Windows 的听力卷、期中范围、辨音选词、填词、听力规则，并集成 **ncnn-android-piper** 作为本地 TTS 备用。

## 听力卷

- 第一节 1–5：听句子，从 3 个发音相近的词中选出句子里出现的那个。
- 第二节 6–10：5 段短对话，每题 3 个选项。
- 第三节 11–20：3 段长对话，共 10 题。
- 第四节 21–25：1 篇短文，5 题。
- 第五节 26–30：1 篇短文 + 信息表，5 个填词，短文读三遍。

七、八年级单元按 `../unit.md` 的 2024 版 PEP 新教材；考试范围新增 **期中考试（前半册）**。出题提示词的难度标签为 **“七年级PEP版英语 / 八年级PEP版英语 / 九年级PEP版英语”**。

## 听力规则

- 第一节和第二节先读题号（第 1 题、第 6 题……）再读材料。
- 长对话读题号范围；短文/填词读“第 21 至 25 题”“第 26 至 30 题”。
- 试音是固定英文麦克风测试，不再插入 2 分钟静音：
  > Hello, this is a microphone test. Testing, one, two, three. The quick brown fox jumps over the lazy dog. Can you hear me clearly? If so, we are ready to begin.
- **听力只播一次**：开始后不能暂停、拖动或回放；退出时可以选择“批改已选题目并退出”或“直接退出”。
- 不朗读题干和选项。

## 语音：Edge 优先，本地 ncnn 兜底

1. **优先线上 Edge**：使用 `en-US-AvaMultilingualNeural`，中英双语、整套 MP3。
2. Edge 失败时，如果本地模型已安装，自动回退到 **ncnn-android-piper CPU**，按中/英文分别合成后拼成 WAV。
3. 本地模型是可选下载，不打包进 APK；APK 只带词表和 arm64 native 库。

### 首次打开

会提示：

> 下载本地语音模型？

- 下载：从 `hf-mirror.com` 拉取英文 + 中文 ncnn 模型，约 65 MB；进度可见。
- 暂不：继续使用 Edge；之后可在设置里下载。
- 设置里可以重新下载或删除模型。

模型源：

- `https://hf-mirror.com/gyroing/PiperTTS-NCNN-Models/resolve/main/en_US-girl.zip`
- `https://hf-mirror.com/gyroing/PiperTTS-NCNN-Models/resolve/main/zh_CN-huayan.zip`

词表随 APK 放在 `assets/piper/`，下载后写入 App 私有目录 `files/piper/`。

## 安装

```bat
adb install -r Android\EchoStep-1.0.apk
```

APK 只包含 `arm64-v8a` native 库；绝大多数现代手机可用。

## 构建

```bat
cd D:\dsh-work\English\Android
build.bat :app:assembleRelease
```

环境：JDK 17、Android SDK 35、NDK 28.2、CMake 3.22.1。

## 代码结构

```text
Android/
├─ EchoStep-1.0.apk
├─ app/src/main/assets/
│  ├─ curriculum.json
│  └─ piper/                    # en/zh 词表
├─ app/src/main/java/com/echostep/app/
│  ├─ MainActivity.java          # 模板 UI、音频锁、退出批改、模型下载
│  ├─ audio/
│  │  ├─ ExamAudioTextBuilder.java
│  │  ├─ LocalPiperTts.java      # ncnn 本地合成 + WAV 拼接
│  │  ├─ PiperModelManager.java  # hf-mirror 下载/解压
│  │  └─ PiperNcnn.java          # JNI
│  ├─ net/                       # DeepSeek + Edge + PromptBuilder + TemplateParser
│  └─ data/                      # 试卷/题型/单元目录
└─ app/src/main/jni/
   ├─ piper.cpp / simpleg2p.cpp
   ├─ piperncnn.cpp
   └─ ncnn-20261020-android/     # CPU ncnn
```

## 安全与限制

- API Key 存在 App 私有 SharedPreferences，`allowBackup=false`。
- 本地模型下载使用 HTTPS；模型只写入 App 私有目录。
- Edge 是微软公开但非官方接口，可能变化；本地 ncnn 是兜底，不保证所有机型速度。
- 本地合成是 CPU 推理，长音频会比 Edge 慢；因此默认 Edge 优先。
- 第一次使用本地兜底时需要先下载模型，否则 Edge 失败会明确提示。
