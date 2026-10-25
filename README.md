# 全能 v0.2.1

安卓播放器 + AI 工作台 + 发现与方案页。React 19 + Tailwind CSS 4 + Android WebView 离线打包，包含默认原生视频播放页。

- 本地视频、HTTPS 视频直链、开放 M3U8、网页媒体发现。
- 前后 10秒/1分钟/10分钟、A—B 区间循环、倍速、字幕与播放列表。
- 配置自己的 OpenAI 兼容接口使用 AI 对话；密钥只保留在当前会话。
- 移动端底部导航、沉浸主视觉、功能卡片与方案展示；Pro 未开售，无支付功能。

`frontend/` 是当前应用和网站界面；`android/` 是安卓宿主与构建工具；`dist/` 保留 v0.1 网页版；`docs/ANDROID.md` 记录使用、构建与验证范围；`docs/OPEN_SOURCE_REVIEW.md` 记录开源方案比较。

```bash
npm ci --prefix frontend
npm test --prefix frontend
npm run build --prefix frontend
```

网络播放受网站访问权限、跨域、DRM及设备解码限制，不保证所有网址均可播放。网页媒体发现不绕过登录、会员或加密。真实 AI 需要用户自己的服务商接口与密钥。
