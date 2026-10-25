# 全能 Android v0.2.3

## 使用

应用名「全能」，包名 `app.quanneng`，支持 Android 8.0（API 26）及以上。界面随 APK 离线打包，无需网页账号。含「播放」「AI 工作台」「发现」三个入口。

- 播放：本地选择多个视频；输入 HTTPS 媒体直链；前后 10秒/1分钟/10分钟；A—B 区间循环；0.5–3倍速；SRT/VTT 字幕；单片循环和自动下一集。
- 网页：输入普通网页地址，点「打开网页 · 发现视频」，在网页内开始播放后点「发现视频」，选择公开加载的媒体地址。网页使用单独的 WebView，不暴露应用接口。
- 原生播放：安卓网络视频默认使用系统媒体栈；也支持通过文件管理器打开 content:// 视频。系统播放页含跳转、AB循环和倍速。
- AI：设置 HTTPS 接口根地址、模型 ID 和 API Key，使用兼容 Chat Completions 的服务。密钥与对话仅保存在本次会话，不写入磁盘。服务商费用、内容处理政策由服务商决定。可取消生成。
- 发现：产品主视觉、六张功能卡片、基础版与 Pro 规划方案区；无订阅、支付、扣费功能。

## 构建

依赖：Node.js 20.19+ 或 22.12+，Java 17+，Android SDK Platform 35 和 Build Tools 35.0.0，Python 3 标准库。默认使用 javac；只有 JRE 时可用官方 Maven Central 上的 `org.eclipse.jdt:ecj:3.38.0` 并传 `--compiler-jar`。

```bash
npm ci --prefix frontend
npm test --prefix frontend
npm run build --prefix frontend
python3 android/tools/build.py --sdk /path/to/sdk-unpacked --keystore /private/quanneng-signing.p12 --out /path/to/Quanneng-v0.2.3.apk
```

脚本期望 SDK 目录里含 `android-15/`（Build Tools 35.0.0 解压目录）与 `android-35/android.jar`。自动生成测试签名证书，默认密码 `android`；可用环境变量 `QUANNENG_STORE_PASSWORD` 指定。保留私有签名文件以便后续覆盖升级；不要提交签名文件到 Git。当前交付为测试签名版。

## 已知限制与验证范围

视频是否可播放受来源的跨域、Cookie、Referer、防盗链、DRM与设备解码能力限制。网页发现无法保证所有网站或 blob/MediaSource/加密视频可用；不绕过登录或付费限制。网页发现的来源 Cookie/Referer 仅在本机内存转交给选定的视频地址，不会保存到播放列表。网页内嵌跨域 iframe 可能无法发现。HLS 请求需要来源允许应用域跨域访问；可尝试系统播放。

应用采用 React + Tailwind CSS 离线界面、Android WebView 与原生系统播放后备引擎；没有集成完整 Media3/FFmpeg 解码包。Android 系统 WebView 建议保持更新。

已完成核心单测、生产构建、Java 编译、Dex 转换、资源打包及 APK 签名校验；浏览器验证覆盖视频播放、AB循环、倍速、页面切换和缺少 AI 配置时的提示。尚未在真实安卓手机上运行验收，未使用真实 API Key 测试模型响应。不要把编译通过等同于所有机型兼容。

## v0.2.1 网络播放修复

安卓网络视频默认进入原生播放页，避免以网页跨域策略加载媒体。网页发现媒体时，Referer、User-Agent 及该媒体 URL 适用的 Cookie 只在本机内存转交；Cookie 不写入播放列表。没有明确视频后缀的普通网页地址会先检查 Content-Type，HTML 转入网页发现流程。原生页保留快进、倍速、AB 循环、文件 URI、字幕导入及可用时的网页回退；加密、失效地址与设备不支持的编码仍需分别处理。

增加隔离的 Android 仪器测试 APK，通过默认分享入口启动 HTTPS 视频，检查视频尺寸和播放进度实际推进、AB 循环，以及 content:// 本地媒体。测试 APK、公开测试视频和导出的测试 Provider 均不会进入用户安装包。

## v0.2.2 播放界面与全屏

深色原生播放页采用统一的薄荷绿主播放键、48dp 以上触摸控件、分组跳转卡片和 A/B 取点卡片。进度条支持拖动；倍速切换保持原本的播放/暂停状态。全屏键切换横屏沉浸模式，保留播放器与进度；轻点画面唤出控制，播放时 4 秒自动收起，暂停和读屏触摸探索时保持显示。系统返回先退出全屏，再返回列表；顶部返回键也可退出全屏。切换全屏不重建或重新加载媒体。

扩展 Android 仪器测试覆盖横竖屏布局、暂停状态、全屏后继续播放、控制显隐和返回行为，并保留网络解码与 A/B 循环回归。尚未在用户真机上验收。

仪器测试在独立临时模拟器中将系统首次沉浸教学提示标为已阅读，这与 Android CTS 的 DisableImmersiveModeConfirmationRule 使用同一前置设置；不改变用户手机设置，不给安装包添加系统设置权限。用户首次进入全屏时如看到系统“知道了 / Got it”提示，正常点击后即可使用播放器。

## v0.2.3 精准跳转

竖屏六档跳转改为带方向图标、分层数字和描边的时间块。横屏全屏直接显示 −10分、−1分、−10秒、+10秒、+1分、+10分，所有触摸目标不少于48dp。按压缩放90ms、回弹160ms，叠加原生涟漪；跳转后视频区域显示方向和目标时间。连续点击会累加目标，接近起止边界时明确反馈；暂停跳转不自动播放。按系统动画关闭设置自动省略位移动效。

新增1300秒合成测试媒体，验证六档实际跳转、起止截断、连续点击、暂停保持与全屏按钮边界；不会将测试视频放入正式APK。

## v0.2.4 changes and verification

- Removed AI chat, configuration UI, Chat Completions requests and their JavaScript bridge methods.
- Added device-local website bookmarks with naming/editing, search, deduplication, deletion confirmation and visible storage failure. Existing video queue remains separate.
- Added feature-gated AndroidX document-start detection in cross-origin frames, fetch/XHR MIME observation, extensionless source collection and automatic count updates. A blob URL is never handed to native MediaPlayer. When the page plays without an independent source, the UI says so and retains webpage viewing.
- Added `BrowserSmoke`: isolated HTTPS fixture origins delivered through a test-only WebViewClient, actual MP4-to-blob playback, extensionless fetch/XHR, cross-origin iframe source/referrer preservation; real offline React UI bookmark add/rename/reopen/delete and removed AI bridge assertions.
- Fixture origins and network stubs exist only in the instrumentation APK. Tests never visit the user's page or access their session. Production does not trust test certificates or allow cleartext traffic.
- Build tool fetches two pinned Apache-2.0 AndroidX AARs from Google Maven, checks hashes, includes resource IDs and dex classes; source app remains Java 8, minimum Android 8 / API 26.

The current v0.2.4 player uses GSYVideoPlayer 14.0.0's Media3 1.10.1 backend, with Media3 HLS/DASH modules and text subtitle rendering. The earlier v0.2.x descriptions of platform VideoView/MediaPlayer are historical. `PlaybackSmoke` now asserts native dimensions/position through the adapter and first tests public Shaka HLS and DASH fixtures, then MP4, AB, full-screen, all six jumps and local content playback. The dependency lock records every included AAR/JAR and checksum; no Cast, service/session, IJK native libraries or downloaded executable is included.


## v0.2.5 六平台入口与用户导出视频保存

- 「平台导入」页包含百度网盘、B 站、抖音、快手、腾讯视频、爱奇艺六个官方网页入口。入口仅打开 HTTPS 网站，不假装拥有平台下载接口。
- 「导入并保存到手机」通过 ACTION_OPEN_DOCUMENT 选择用户可读的普通视频，再通过 ACTION_CREATE_DOCUMENT 选择保存目标。64 KiB 分块复制，文件不会上传至第三方服务器。成功后尝试交给原生播放器打开。
- Android 支持从其他应用通过 ACTION_SEND (video/*) 和 ACTION_SEND_MULTIPLE (video/*) 分享普通文件到全能。已有文本链接分享入口仍保留。
- 不能访问 Android/data、其他 APP 私有缓存或破解 DRM；来源无法提供可读取文件时会失败并提示。文件选择器授权仍受 Android 存储限制。
- 复制流程与六个平台的真实登录、播放、导出行为尚待真实机型验收；前端单测和 CI 安卓构建不能代替六个服务端到端验证。
