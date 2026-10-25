# 全能 Android v0.2.0

## 使用

应用名「全能」，包名 `app.quanneng`，支持 Android 8.0（API 26）及以上。界面随 APK 离线打包，无需网页账号。含「播放」「AI 工作台」「发现」三个入口。

- 播放：本地选择多个视频；输入 HTTPS 媒体直链；前后 10秒/1分钟/10分钟；A—B 区间循环；0.5–3倍速；SRT/VTT 字幕；单片循环和自动下一集。
- 网页：输入普通网页地址，点「打开网页 · 发现视频」，在网页内开始播放后点「发现视频」，选择公开加载的媒体地址。网页使用单独的 WebView，不暴露应用接口。
- 系统播放：当 WebView 不兼容网络视频时，尝试 Android 系统解码；也支持通过文件管理器打开 content:// 视频。系统播放页含跳转、AB循环和倍速。
- AI：设置 HTTPS 接口根地址、模型 ID 和 API Key，使用兼容 Chat Completions 的服务。密钥与对话仅保存在本次会话，不写入磁盘。服务商费用、内容处理政策由服务商决定。可取消生成。
- 发现：产品主视觉、六张功能卡片、基础版与 Pro 规划方案区；无订阅、支付、扣费功能。

## 构建

依赖：Node.js 20.19+ 或 22.12+，Java 17+，Android SDK Platform 35 和 Build Tools 35.0.0，Python 3 标准库。默认使用 javac；只有 JRE 时可用官方 Maven Central 上的 `org.eclipse.jdt:ecj:3.38.0` 并传 `--compiler-jar`。

```bash
npm ci --prefix frontend
npm test --prefix frontend
npm run build --prefix frontend
python3 android/tools/build.py --sdk /path/to/sdk-unpacked --keystore /private/quanneng-signing.p12 --out /path/to/Quanneng-v0.2.0.apk
```

脚本期望 SDK 目录里含 `android-15/`（Build Tools 35.0.0 解压目录）与 `android-35/android.jar`。自动生成测试签名证书，默认密码 `android`；可用环境变量 `QUANNENG_STORE_PASSWORD` 指定。保留私有签名文件以便后续覆盖升级；不要提交签名文件到 Git。当前交付为测试签名版。

## 已知限制与验证范围

视频是否可播放受来源的跨域、Cookie、Referer、防盗链、DRM与设备解码能力限制。网页发现无法保证所有网站或 blob/MediaSource/加密视频可用；不绕过登录或付费限制。网页视频交接暂不转移来源 Cookie/Referer。网页内嵌跨域 iframe 可能无法发现。HLS 请求需要来源允许应用域跨域访问；可尝试系统播放。

应用采用 React + Tailwind CSS 离线界面、Android WebView 与原生系统播放后备引擎；没有集成完整 Media3/FFmpeg 解码包。Android 系统 WebView 建议保持更新。

已完成核心单测、生产构建、Java 编译、Dex 转换、资源打包及 APK 签名校验；浏览器验证覆盖视频播放、AB循环、倍速、页面切换和缺少 AI 配置时的提示。尚未在真实安卓手机上运行验收，未使用真实 API Key 测试模型响应。不要把编译通过等同于所有机型兼容。
