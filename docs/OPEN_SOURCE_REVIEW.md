# 开源方案比较与采用记录

## v0.5.1 视频语音翻译

采用 Foxlet Translate v0.5.0（MIT，AAR SHA-256 `5dbb384907d69c2aba129fd15dba30dda94ede0fa556dc6a167d041ee479ec0e`），提供 Android 9+ 的英语字幕到界面所列语言的离线推理。模型从 Mozilla 发布源按固定清单下载并逐项校验，模型独立适用 MPL-2.0。AAR 内含许可及来源声明；Kotlin stdlib 2.4.10、coroutines 1.10.2 的下载散列固定在依赖锁文件。字幕内容只在本机转换。网络用于首次下载翻译模型，不传输用户语音或字幕。

检查时间：2026-10-25。

| 项目 | 维护与许可证 | 实现思路、依赖 | 注意事项与本次取舍 |
|---|---|---|---|
| nextlevelbuilder/ui-ux-pro-max-skill | 官方 CLI ui-ux-pro-max-cli 2.15.0；MIT | Python 标准库本地 BM25 检索、设计系统规划、React/Tailwind指南 | 已安装为当前环境技能并实际调用。设计数据库给出建议，需结合已有品牌与手机交互复核。 |
| moneytoo/Player（Just Player） | 2026-10-22 有推送；Unlicense | Java + AndroidX Media3，支持系统文件/链接打开 | 原生播放设计成熟，但 Media3 模块与系统解码仍有格式限制；音频不是主要支持目标。本版借鉴系统 Intent 和独立播放页设计，没有复制代码。 |
| anilbeesetti/nextplayer | 2026-10-24 有推送；GPL-3.0 | Kotlin / Compose / Media3，媒体库、字幕、可选 FFmpeg 能力 | 功能完整但工程、依赖及许可义务更重，本版未复制或链接其代码。 |
| video-dev/hls.js | 采用 1.6.13；Apache-2.0 | WebView/浏览器 MediaSource HLS 播放 | 需要 MSE 与来源 CORS；不是任意网页解析器。已复用官方 npm 包。 |

最终方案：保留已经验证的播放器行为；React + Tailwind 重建多入口界面；Android WebView 加载 APK 内的可信资源；原生系统播放器作为后备。后续扩大格式支持时，优先评估 Media3，不将「全能」解释为所有网址或编码的绝对承诺。

设计规则在 `design-system/quanneng/MASTER.md`。UI UX Pro Max 的 OLED暗色、明确主操作、键盘焦点、48dp点击区域与移动端导航建议已应用；强调色保留已有浅绿以延续用户认知。无需外部字体请求。

第三方许可证保留在 `dist/vendor/HLS-LICENSE`；前端 npm 依赖许可证见各包，项目安装清单固定版本并提交锁文件。React、React DOM、Tailwind、Vite、Phosphor Icons 为 MIT，hls.js 为 Apache-2.0。编译备用工具 Eclipse JDT ECJ 为 EPL-2.0，仅构建使用，不随 APK 分发。

### v0.2.1 回归测试方案

采用 ReactiveCircus/android-emulator-runner（Apache-2.0，v2 发布版提交 a421e43855164a8197daf9d8d40fe71c6996bb0d）在 GitHub Linux 临时 runner 上运行加速 Android 模拟器；以 android-actions/setup-android（MIT，提交 035682743ab223ec9c6169af09811dff8247e520）准备官方 SDK 工具。前者依赖可用 KVM，启动和镜像下载需要时间；后者解决 runner 未预装 sdkmanager 的问题。使用 Android 官方 Instrumentation 和 VideoView API，不引入生产环境第三方播放器源码。VideoView 的带请求头 setVideoURI 接口用于媒体来源上下文传递。

### v0.2.2 播放控件与沉浸设计

参考 moneytoo/Player（Unlicense，2026-10-22 有维护提交）的独立主播放键、集中传输控制和轻点显示控制的设计。该项目依赖 Media3/ExoPlayer，扩大依赖和切换媒体栈不在本次 UI 修复范围；保留现有 VideoView，使用 Android 官方 WindowInsetsController（API 30+）与旧系统沉浸标志实现全屏。Android 官方文档说明系统栏可由边缘手势临时唤出，桌面窗口的标题栏也可能保留，因此不拦截系统导航手势。未复制第三方播放器源码。颜色、矢量图标和布局由本项目实现。

官方资料：https://developer.android.com/develop/ui/views/layout/immersive ；项目：https://github.com/moneytoo/Player 。

Android CTS（Apache-2.0）的 DisableImmersiveModeConfirmationRule 将 IMMERSIVE_MODE_CONFIRMATIONS 设为 confirmed，以免系统教学窗口干扰被测应用的输入。CI 采用相同的隔离模拟器前置条件，未在应用中写入这一设置。参考：https://android.googlesource.com/platform/cts/+/0c55e5e18e604b0b890dc73e2d63a0cd6f2b1159/tests/inputmethod/util/src/android/view/inputmethod/cts/util/DisableImmersiveModeConfirmationRule.java 。

## v0.2.4 — frame and dynamic media observation

Reviewed 2026-10-25 before implementation:

| Project | License / maintenance | Approach and dependencies | Decision / known limits |
| --- | --- | --- | --- |
| [nktnet1/webview-kiosk](https://github.com/nktnet1/webview-kiosk) | AGPL-3.0; GitHub pushed_at 2026-10-24 | Full Android WebView browser shell and configurable page injection | Considered integration patterns only; no source copied or dependency added. A whole kiosk browser adds unrelated device controls and does not guarantee transferable video URLs. |
| [AndroidX WebKit](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/webkit/) | Apache-2.0; actively maintained AndroidX | `addDocumentStartJavaScript` injects before page JS in matching frames; `addWebMessageListener` returns frame-origin and a reply proxy. Pinned 1.14.0 + Core 1.1.0, Google Maven AARs with SHA-256 checks. | Chosen official APIs. Requires feature checks on installed WebView; older providers retain direct/same-origin detection. No third-party application source copied. |

Primary references: [official WebViewCompat API](https://developer.android.com/reference/androidx/webkit/WebViewCompat), [WebKit releases](https://developer.android.com/jetpack/androidx/releases/webkit), [WebKit 1.14.0 POM and Apache license declaration](https://dl.google.com/dl/android/maven2/androidx/webkit/webkit/1.14.0/webkit-1.14.0.pom).

Original bounded script observes direct video sources, performance resource URLs and successful fetch/XHR media MIME types. It does not consume response bodies, rewrite responses, intercept DRM keys, or export blob/segment URLs as playable sources. The listener accepts untrusted candidate data only, validates HTTPS and frame origin, caps lengths/counts, and never starts activities, reads files, or returns credentials to pages. Network headers remain sanitized, bounded and in memory. DRM, WebRTC, opaque responses and private segmented protocols are not universally extractable; native MediaPlayer codec/stream limits still apply.

### Added playback engine and mrowser design reference

- **GSYVideoPlayer 14.0.0**, Apache-2.0, upstream GitHub pushed 2026-10-10. Maven Central's stable release metadata is authoritative (13.3.0 appeared in README but was not published). The actual `gsyvideoplayer-exo2` / `gsyvideoplayer-base` artifacts use **Media3 1.10.1**, plus Apache-2.0 GSYIjkJava 1.0.0. `MatureVideoView` is a thin original UI adapter around the unmodified published `IjkExo2MediaPlayer`; the app no longer uses platform VideoView for playback. It retains GSY source construction, load control, renderer selection and Media3 decoding. Protocol modules include HLS, DASH, progressive, RTSP and SmoothStreaming; UI only accepts HTTPS and content URIs. Cast/session/UI modules and native RTMP/IJK codecs are intentionally excluded from the reviewed dependency subset. The app does not advertise those excluded features.
- **m-salehi-v/mrowser**, MIT, upstream GitHub pushed 2026-10-22. Reviewed `MediaUrlClassifier`, `StreamSniffer`, `StreamCandidateSelector` and `SniffingWebViewClient`: classify manifest/file/subtitle/segment separately, prefer HLS master/HLS/DASH over preview files, preserve request context and reset candidates per navigation. Implemented independent Java policy and generation checks; no Kotlin app code or GPL block lists copied. MIME observation and cross-frame script are original additions, preserving extensionless manifests through to GSY's override-extension option.
- Media3 alone supplies a mature playback API but would require rebuilding orchestration; GSY's existing Media3 backend fits the custom full-screen controls without embedding a second UI framework. Neither guarantees extraction of DRM/private web protocols. GSY upstream notes fullscreen pause and source/header issues in past versions, so Android tests retain paused seek, orientation, AB and content URI coverage and add real HLS/DASH playback.

Sources: [published GSY Exo POM](https://repo.maven.apache.org/maven2/io/github/carguo/gsyvideoplayer-exo2/14.0.0/gsyvideoplayer-exo2-14.0.0.pom), [GSY update history](https://github.com/CarGuo/GSYVideoPlayer/blob/master/doc/UPDATE_VERSION_EN.md), [mrowser source and license](https://github.com/m-salehi-v/mrowser). Exact resolved artifact hashes and coordinates are in `android/dependencies.lock.json`.

## v0.2.6 — supported-site download, ordinary conversion and private access

Reviewed upstream primary sources on 2026-10-25:

| Project | License / maintenance | Use and constraints |
| --- | --- | --- |
| [YTDLnis](https://github.com/deniscerri/ytdlnis) | GPL-3.0; actively maintained Android downloader | Reviewed the supported-site and separate file-import workflow. No YTDLnis UI/application source copied, and its name is not used as this app's name. |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | Source/wheel Unlicense; executable dependencies have their own licenses | Actual supported-site extractor engine. No raw user flags, cookie scraping, DRM keys or unplayable-format override. An extractor being present does not guarantee a site accepts a request or session. |
| [youtubedl-android](https://github.com/yausername/youtubedl-android) | GPL-3.0; official Maven 0.18.1 | Chosen mature Android binding, embedded Python/QuickJS and FFmpeg. Uses reviewed SHA-256 pinned official Maven AARs, selected native ABI and application-private workspace. Kotlin and Jackson/Commons dependency versions are pinned in the lock. Unused AppCompat UI is not embedded. GPL-3.0 applies to the combined Quanneng application; root LICENSE and packaged GPL text included. |
| [FFmpeg](https://ffmpeg.org/) | Depends on build; upstream wrapper's distribution GPL-3.0 | Executes as a bundled native process; converts ordinary local files to H.264/AAC MP4. No decryption or private-cache access path. Runtime smoke verifies build flags do not enable nonfree and converts a public CC0 clip to a decodable MP4. |

Primary documentation: [wrapper usage/ABI installation](https://github.com/yausername/youtubedl-android/blob/master/README.md), [wrapper source and native build notes](https://github.com/yausername/youtubedl-android/blob/master/BUILD_FFMPEG.md), [yt-dlp supported sites](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md), [FFmpeg legal/build configuration](https://ffmpeg.org/legal.html). Known upstream issue: some 0.18.1 FFmpeg payload dependencies, including libwebp, have reported 16 KB-page compatibility gaps. This release targets the user's arm64 Android 13 device and tests x86_64 Android 15 on 4 KB pages; it does not claim 16 KB-device verification. QuickJS process startup can take several seconds; initialization and execution run off the UI thread.

Private access follows platform [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [FLAG_SECURE](https://developer.android.com/security/fraud-prevention/activities) and [foreground-service requirements](https://developer.android.com/develop/background-work/services/fgs/launch). PBKDF2-HMAC-SHA256 (210,000 rounds, 32-byte random salt) output is authenticated with an app-specific non-exportable Android Keystore HMAC key. Stored verifier comparison uses MessageDigest.isEqual. Persistent failure backoff begins after five failed attempts. UI/session checks do not expose passwords, reset links or alternate unlock methods.

## v0.3.0：媒体增强（2026-10-26）

- **继续使用现有 Media3 / GSY、HLS.js、yt-dlp-android / FFmpeg**：避免另换播放器和下载内核；版本及散列继续固定。
- **WebDAV**：比较了 thegrizzlylabs/sardine-android（Apache-2.0，检索时最近代码推送为 2024-02）及其 OkHttp PROPFIND 方案。当前只需要 Depth:1 浏览、Basic 会话认证与播放，采用 OkHttp 4.12.0 + Okio 3.6.0 的小范围实现，不复制 Sardine 代码。两个版本与现有 Kotlin 2.0.20 运行时兼容，坐标和 SHA-256 已固定。关闭自动重定向，认证播放数据源限定同源，防止 NAS 凭据转发到外部站点。Digest、OAuth、WebDAV 写操作未实现。
- **DLNA**：检索 jupnp/jupnp（UPnP/DLNA，检索时有 2026-10-25 推送；仓库许可证元数据 NOASSERTION，未直接引入）。实现范围限定于 SSDP M-SEARCH、设备 XML 描述与 AVTransport SOAP，不复制其代码，不引入完整服务端。需电视支持 MediaRenderer，暂不处理 Chromecast、Miracast 或手机文件 HTTP 服务。用模拟设备验证协议，真实电视仍需验收。
- **字幕**：核对 androidx/media #1976，Media3 字幕偏移没有简单稳定的通用运行时设置；因此对用户导入的 SRT/VTT 改写时间轴后重新加载，并保留播放位置。不修改 Media3 内部 Renderer。
- **保险箱**：遵循 Android Keystore 的 AES/GCM/NoPadding。大视频分块认证，AAD 绑定文件随机 ID、帧序号及帧用途，终止帧用于拒绝截断；内存有界。仅在全部认证完成后发布临时播放文件，后台/退出/启动清理。未依赖不明来源的加密实现。

参考入口：
- https://github.com/thegrizzlylabs/sardine-android
- https://github.com/square/okhttp
- https://github.com/square/okio
- https://github.com/jupnp/jupnp
- https://github.com/androidx/media/issues/1976
- https://developer.android.com/privacy-and-security/cryptography


## v0.3.1：本地视频临时投屏与接力

比较 NanoHttpd/nanohttpd（BSD-3-Clause，仓库最近推送 2023-07）与 yanzhenjie/AndServer（Apache-2.0，最近推送 2023-06）。两者均非近期活跃发布，未把它们描述为持续活跃维护。选择固定 NanoHTTPD 2.3.1 的小型核心作为 HTTP 解析和连接层，避免引入 Web 框架及注解构建链；SHA-256 与 Maven 坐标已固定，完整 BSD 许可证随包附带。应用未启用其目录、上传或文件服务器扩展，只发布一个已选择的视频副本；自己的封装限制私网来源、随机会话路径、方法、单一 Range、30 分钟时限和 4 个工作连接/4 个排队连接。测试覆盖端点边界、后台生命周期、访问撤销及真实 Media3 读取。

### v0.3.2 系统验证与横屏交互

本次不增加外部依赖。系统验证使用 Android 平台 BiometricPrompt（API 28+）和 KeyguardManager 凭据页，API 30+ 请求 BIOMETRIC_STRONG | DEVICE_CREDENTIAL，API 29 使用其兼容凭据标志。系统取消不会切换为成功；请求与会话 generation 绑定，屏幕关闭或手动锁定会废弃旧授权。未设系统锁屏时可打开系统安全设置，或使用既有 PBKDF2 私人密码流程。后台授权仅保留于进程内 5 分钟，屏幕关闭立即失效。横屏控制避免导航栏与系统手势区域，触摸探索继续采用原生 SeekBar。

参考：Android 官方 BiometricPrompt.Builder API、KeyguardManager API 与 edge-to-edge system gesture insets 文档。真实设备指纹及不同厂商布局仍需实测。
