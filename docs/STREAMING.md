# v0.4.1 流畅播放与剧集

## 开源方案

| 方案 | 许可与取舍 |
| --- | --- |
| AndroidX Media3 1.10.1 | Apache-2.0，官方项目持续维护。使用 SimpleCache、LRU、CacheDataSource、LoadControl、自适应轨道选择和错误恢复。 |
| GSYVideoPlayer 14.0.0 | Apache-2.0，最近代码推送 2026-10-10。沿用现有内核，通过公开接口注入媒体源、轨道与加载控制。 |
| Media3 datasource-okhttp 1.10.1、OkHttp 4.12.0 | Apache-2.0。新增官方网络桥接模块，复用既有 OkHttp/Okio 与连接池。协议由客户端和服务端协商，不承诺 HTTP/3 或跑满带宽。依赖版本与 SHA-256 固定。 |
| danikula/AndroidVideoCache | Apache-2.0，仓库最近推送 2023-03-14。未采用其额外代理缓存来替代 Media3 的分片缓存，避免增加本地代理和第二套缓存。 |

核对官方文档与实际发布版本 source JAR，未复制其他播放器应用代码：

- https://developer.android.com/media/media3/exoplayer/network-stacks
- https://github.com/androidx/media
- https://github.com/CarGuo/GSYVideoPlayer
- https://github.com/square/okhttp
- https://github.com/danikula/AndroidVideoCache

## 缓冲、缓存与弱网

默认“流畅优先”，流媒体最小/最大缓冲目标 30/120 秒；均衡及省流量为 15/60 秒。首次播放目标 2.5 秒，重新缓冲后恢复目标 8 秒。普通设备内存目标 64 MiB、低内存设备 32 MiB，优先遵守容量目标，保留约 10 秒后向内存缓冲。以上是加载目标，实际受码率、带宽、直播窗口和容量影响。本地文件保留默认加载控制。

保留官方自适应轨道选择，源站提供多档 HLS/DASH 时可按带宽切换；省流量额外优先 720p/1.5 Mbit/s 以下。单码率 MP4 无法自动生成低码率版本。

公共 HTTPS 媒体边播边缓存，LRU 上限 512 MiB。缓存键对完整链接和请求上下文做 SHA-256，保留查询串，区分 Referer，不擅自去掉签名参数。带 Cookie/Authorization 的来源、局域网 HTTP、NAS、本地及保险箱不写入这份缓存。HLS/DASH/SmoothStreaming 清单保持网络刷新，HLS 密钥和外置字幕不缓存。缓存不是完整下载，系统可回收，链接变化会形成新键。

统一网络错误策略使用 Media3 错误分类与回退，最低重试计数 6，间隔约 0.5–5 秒。解析错误、越界和多数 HTTP 4xx 不持续重试，408/429 和可恢复网络错误仍可重试。各媒体模块决定任务何时结束，初始连接仍受原有 40 秒应用超时约束。不能绕过源站限速、登录、过期签名或 DRM。

播放页显示已缓冲秒数和缓存状态，可选策略、关闭缓存和异步清理。策略及缓存开关在下次打开视频生效；横屏从倍速菜单进入。重新缓冲时显示等待状态。网页后备 HLS.js 限制前后缓冲，其内部恢复失败后最多额外执行三次延迟网络重启和一次解码恢复；切源取消旧任务。网页后备不共享原生磁盘缓存。

## 同一部剧的选集

首页“添加一部剧”输入剧名与各集 HTTPS 视频直链，每行一集，可写 `集名|链接`，按填写顺序分组保存。每组及首页列表最多 100 条。普通剧集网页不会自动变成全集，暂未做各网站专属全集抓取器。

当前剧有两集以上时，横竖屏显示选集、当前集与上下集，可关闭连播，到首尾禁用对应按钮。手动切集恢复该集进度，自动下一集从头播放。只有一集或独立视频隐藏选集与上下集。独立视频和其他剧不会进入当前剧的队列。

系统多选文件可作为一次剧集播放；本地媒体库按同一已授权目录分组、自然文件名排序，不混入其他目录。网页发现的多条结果可能是画质或分片，不能将它们冒充剧集列表。

## 验证

前端测试覆盖分组、跨剧隔离、单集、无效输入及恢复边界。Android instrumentation 使用真实 Media3 缓存和解码器，检查精确字节、断网 Range、上下文隔离、私密源不落盘、限速源一次中断后的恢复及缓存重播/拖动。原有公开 HTTPS HLS/DASH/MP4 播放回归继续运行。剧集检查选择、当前标记、上下集边界、真实播完切集和关闭连播。

外置字幕改用 Media3 的提取期字幕解析，输出当前渲染器支持的 cues，避免旧版 SingleSampleMediaSource 所需的已默认关闭的 legacy 解码路径，并检查实际字幕控件出现台词。

合成视频和可控数据源不等同于所有手机、CDN 和 Wi-Fi 环境；不承诺任何网络条件下零卡顿。

## v0.4.2 网页点播与 3003 兼容

- 网页观察不再枚举全部 DOM 视频、performance 资源或 WebView 预加载请求。只有用户在对应 frame 中发生真实触摸/按键后的视频 play 事件，才启动当前视频会话；取得独立地址后自动转接全能。
- 直接视频使用 currentSrc，fetch/XHR Blob 会追踪到该 Blob 的实际来源。MSE 仅在当前点播 frame 和短暂的用户操作窗口观察媒体请求，排除已知初始化/媒体分片；无独立地址和受保护媒体留在网页播放。通用网页播放器并不公开可靠的视频与每个网络请求的对应关系，不能保证所有网站均能转接。
- 当前视频自动备注页面/视频标题及页面公开的选中集数，播放列表保存名称与来源网页并显示网站。不会从 URL 猜造集数，也不持久化 Cookie。
- 原生媒体链路在后台通过同一请求上下文读取最多 8 KiB，优先识别实际 HLS/DASH/MP4/WebM 内容，再选 Media3 source，兼容无后缀、签名 URL 和错误 Content-Type。探测使用普通 GET 并限制读取字节，不依赖源站对 Range 的支持；网络探测失败时仍交给播放器正常请求。收到 HTML/登录验证页会明确提示回原网页，不再只显示模糊的 3003。
- 增加真实 HTTP 源 + Media3 解码检查：无后缀且错误 MIME 的 HLS/DASH、错误 m3u8 后缀的 MP4、拒绝 Range 的 MP4/HLS、伪装为 MP4 的 HTML。网页回归实际触摸跨域 iframe 播放，检查预加载/自动播放不导出、只转接选中的 Blob、名称和来源持久化。

HLS MIME regression: Media3 1.10.1 uses the canonical `application/x-mpegURL` constant in its adaptive source switch. The prior custom factory passed common website aliases (`application/vnd.apple.mpegurl`, lowercase `application/x-mpegurl`) directly into that switch, selecting a progressive file source and producing 3003 for valid HLS. The factory now explicitly maps normalized HLS aliases to `CONTENT_TYPE_HLS`, including after response probing and when restoring subtitle sources. FormatSmoke and the public HLS decode test exercise this boundary.
