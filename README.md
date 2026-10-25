# 全能 v0.2.4

安卓视频播放器与网址收藏。React 19 + Tailwind CSS 4 离线界面，使用 GSYVideoPlayer 14.0.0 + Media3 1.10.1 播放内核。

- 本地视频、HTTPS 视频直链、开放 HLS / DASH、网页媒体发现。
- 前后 10 秒 / 1 分钟 / 10 分钟、A—B 区间循环、倍速、字幕与播放列表。
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
