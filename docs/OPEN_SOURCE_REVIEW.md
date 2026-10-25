# 开源方案比较与采用记录

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
