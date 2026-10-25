# Playback fixture

The instrumentation build downloads the public MDN CC0 flower video from https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4 into `flower.mp4`. This fixture is included only in the test APK, never in the user application. Its bytes are not checked into this repository.

`seek-fixture.mp4` 为 CI 使用 FFmpeg color 源生成的 1300 秒纯色视频（160×90，1 fps，每秒关键帧）；不含第三方影视内容。用于实际验证 10 秒、1 分钟、10 分钟双向跳转、边界与连续点击。生成文件只进入隔离测试 APK，不进入生产 APK。FFmpeg / libx264 仅为 CI 工具，未链接或打包进应用。
