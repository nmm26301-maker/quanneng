# 全能新版 v0.4.3

安卓视频播放器与网址收藏。main 分支当前正式版记录为 v0.4.3（versionCode 15）；最新 v0.4.9 多语言字幕与双指缩放改动仍在 [开放 PR #1](https://github.com/nmm26301-maker/quanneng/pull/1)，尚未合并为正式版。签名备份验收状态见 [安全备份与迁移清单](docs/SECURITY-BACKUP.md)。新增底部「功能中心」，用法与边界见 [功能说明](docs/FEATURES.md)；缓存、弱网优化与同剧选集见 [流畅播放](docs/STREAMING.md)。React 19 + Tailwind CSS 4 离线界面，使用 GSYVideoPlayer 14.0.0 + Media3 1.10.1 播放内核。

- 本地视频、HTTPS 视频直链、开放 HLS / DASH、网页媒体发现。
- 新增六个平台的视频入口：百度网盘、哔哩哔哩、抖音、快手、腾讯视频、爱奇艺；可以从其他应用分享普通视频，或使用 Android 系统文件选择器导入并另存到用户指定位置。
- 四个独立设置的跳转按钮：−秒 / −分钟 / +秒 / +分钟，设置保存在本机，竖屏与全屏同步；另含 A—B 区间循环、倍速、字幕与播放列表。
- 网址收藏：添加、命名、修改、搜索和删除，保存在本机，重启后保留。
- 全能 AI、接口配置与原生 AI 请求桥接已移除。
- 网页检测支持跨域 iframe 的文档启动观察、动态 fetch/XHR 响应类型、无后缀媒体地址；blob 只作为网页播放状态，不作为错误直链导出。

`frontend/` 是当前应用界面；`android/` 是安卓宿主与构建工具；`dist/` 保留 v0.1 网页版；`docs/ANDROID.md` 记录构建和验证范围；`docs/OPEN_SOURCE_REVIEW.md` 记录开源方案比较。

```bash
npm ci --prefix frontend
npm test --prefix frontend
npm run build --prefix frontend
```

增强网页检测需要系统 WebView 支持 DOCUMENT_START_SCRIPT 与 WEB_MESSAGE_LISTENER，不支持时明确提示基础模式。网页能够播放不等于存在可独立转播的 URL：DRM、WebRTC、私有分片协议、会话绑定及设备解码限制仍可能阻止独立播放。不能取得地址时保留原网页播放，不保证任意网页都能转播，也不绕过访问权限。

AndroidX WebKit 1.14.0 和 Core 1.1.0 来自 Google Maven，GSY、Media3 及其依赖的坐标和 SHA-256 固定在 `android/dependencies.lock.json`，许可证见应用内打包的 `third-party-notices.txt`。


六平台入口不表示六家客户端缓存都能直接提取。受 DRM 保护的离线视频、其他应用的私有缓存及需平台授权的内容不能解密/强制导出。平台网页入口只打开网站，能够发现的普通媒体仍受其自身访问限制。文件复制由用户选择源文件和目标位置，不申请广泛文件访问权限。

## 私人版与下载转换

优先使用系统指纹或锁屏密码，无需另设全能密码；没有系统锁屏时才设置备用私人密码。页面切换与短暂返回不重复验证，后台超过 5 分钟、锁屏或手动锁定后再次验证；修改已有私人密码仍需旧密码。下载与转换入口位于“平台导入”页：yt-dlp 下载支持站点的视频，FFmpeg 转换普通本地视频，结果保留在应用私人空间，可播放或经系统文件选择器保存到手机。原应用允许访问的普通视频继续通过系统文件选择器导入。

本应用结合 GPL-3.0 的 Android 下载封装，现以 GPL-3.0 发布，见 LICENSE。源码与构建配置在当前仓库；依赖版本、原始 Maven 地址和 SHA-256 在 android/dependencies.lock.json。默认构建为 arm64-v8a，安卓模拟器另用 --abi x86_64。

## 与旧版并存的正式新版

安装名称为“全能新版”，应用 ID 为 `app.quanneng.next`。旧版 `app.quanneng` 无需卸载，原数据仍在旧版中。新版使用专用固定发布签名，恢复备份单独私密保存，后续新版之间可覆盖升级。普通视频可重新选择，旧版私人数据和保险箱不能自动迁移。详见 [安装与签名恢复](docs/INSTALLATION.md)。
