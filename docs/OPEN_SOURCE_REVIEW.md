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
