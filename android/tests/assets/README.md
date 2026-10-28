# Playback fixture

The instrumentation build downloads the public MDN CC0 flower video from https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4 into `flower.mp4`. This fixture is included only in the test APK, never in the user application. Its bytes are not checked into this repository.

`seek-fixture.mp4` 为本项目使用 FFmpeg color 源生成的 1300 秒纯色视频（160×90，1 fps，每秒关键帧）；不含第三方影视内容。用于实际验证 10 秒、1 分钟、10 分钟双向跳转、边界与连续点击。素材已随测试源码提交，CI 无需安装 FFmpeg；该文件只进入隔离测试 APK，不进入生产 APK。FFmpeg / libx264 仅为素材生成工具，未链接或打包进应用。

本项目自制 `seek-fixture.mp4` 纯色视频按 CC0 提供。SHA-256：`b6e225a56ebc53716f7989131f27a525ce4f3bfe7a5d80b1c885771cbedb9c4a`。

复现命令：
```sh
ffmpeg -f lavfi -i color=c=0x13221b:s=160x90:r=1 -t 1300 -c:v libx264 -pix_fmt yuv420p -g 1 -movflags +faststart seek-fixture.mp4
```

`buffer-fixture.mp4` is an original synthetic FFmpeg testsrc2, 18 seconds, 160×90 at 15 fps, AVC/yuv420p, fast-start MP4, 80 kbit/s target; no external footage/audio. Used only in the instrumentation APK for deterministic throttled, interrupted and offline cache decoding. Generation: `ffmpeg -f lavfi -i testsrc2=size=160x90:rate=15 -t 18 -c:v libx264 -preset veryfast -b:v 80k -maxrate 100k -bufsize 160k -pix_fmt yuv420p -movflags +faststart buffer-fixture.mp4`.

The synthetic buffer fixture is now checked into git, SHA-256 `be659bfae932ea372c136d6daabf686826c6621ea71a847afc60bc9b7b417dbe`. CI verifies those bytes and does not install FFmpeg or regenerate it.

`format/` repackages the same original CC0 buffer fixture into MPEG-TS and DASH/fMP4, without re-encoding. Used only in FormatSmoke's real HTTP server to verify extensionless/mislabeled HLS, DASH and MP4 decoding. Generate with `ffmpeg -i buffer-fixture.mp4 -c copy -bsf:v h264_mp4toannexb -f mpegts format/episode.ts` and `ffmpeg -i buffer-fixture.mp4 -c copy -f dash -seg_duration 20 format/episode.mpd`. These fixtures never enter the release APK.
