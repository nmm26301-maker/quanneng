# 全能新版安装与签名恢复

v0.4.2 安装后显示“全能新版”，独立应用 ID 为 `app.quanneng.next`，可与旧版 `app.quanneng` 共存。请保留旧版安装和数据，保险箱及私有记录继续在旧版使用；普通视频可在新版重新选择。本版包含 v0.3.2 的透明横屏控制、按秒拖动、防误触锁定、按需显示的时钟和系统解锁。

## 固定发布签名

正式版使用专用 RSA-3072 签名，证书 SHA-256：

`bf6f9d9599d42993eb0160efd5d71a070f75d0d5ffc0c0a87bf8bb04d8c8e182`

这把密钥用于新版今后的覆盖更新，不用于替换旧版。发布构建在编译前核对证书，缺少密钥或证书不符立即停止。CI 仅在显式指定 `--development-signing` 时使用独立测试密钥，CI APK 不作为用户正式版交付。

## 私密恢复备份

`QuannengNext-release-signing-backup.zip` 含 `quanneng-next-release.p12`、`signing.json` 和说明。该备份包含恢复口令，只能私密保管，不能上传公开仓库或转发给他人。持久保存一份，并建议用户另下载留存一份。

解压到受保护目录后，使用其中的同一把密钥构建，不能重新生成：

```sh
python3 android/tools/build-release.py --signing-config /private/quanneng-next/signing.json --sdk /path/to/sdk --out /output/QuannengNext.apk
```

ECJ 环境可增加 `--compiler-jar /path/to/ecj.jar`。构建脚本通过进程环境传递口令，不将口令放到命令行或源码中。

## 仓库中的加密恢复副本

`signing/QuannengNext-release-signing-backup.encrypted.json` 保存了上述同一份恢复 ZIP 的加密副本，使用 AES-256-GCM 和 scrypt（N=131072、r=8、p=1）。解密后得到与原备份逐字节相同的 ZIP，SHA-256 为 `03195ef3dc609cb75dc0682e3f88ba8ef173fe9dda7aa0e1645fee2f730950bf`。恢复口令与应用密钥口令不同，单独交付给用户，不写入仓库；公开仓库只存密文、公开证书指纹和恢复脚本。

下载并私密保管 `QuannengNext-signing-recovery-password.json`，安装 Python 的 `cryptography` 依赖后执行：

```sh
python3 -m pip install cryptography
mkdir -m 700 /private/quanneng-next
python3 android/tools/restore-signing.py --password-file /private/QuannengNext-signing-recovery-password.json --out /private/quanneng-next/signing-backup.zip
```

也可省略 `--password-file`，在不回显的提示中输入恢复口令。脚本拒绝错误口令、篡改数据及覆盖已有目标文件；输出 ZIP 权限为 0600。解压后继续使用上文的 `build-release.py`，证书指纹必须保持一致。

## 并存与更新检查

CI 使用单独签名的旧应用标识测试夹具（`app.quanneng`，并占用旧媒体 authority）和新版同时安装。检查两者 UID 与私有数据互相独立，再对新版执行 `adb install -r` 原地重装，验证旧夹具加密标记、新版私有设置和 Android Keystore 加密密钥仍可使用。测试夹具不是用户的真实旧版或用户数据；正式 APK 另校验证书指纹、包名及 provider authority。

## v0.4.1 发布验证

- 应用 ID `app.quanneng.next`，版本号 `0.4.1`，versionCode `13`，正式 arm64-v8a 包继续使用上述固定发布证书，可作为 v0.4.0 的覆盖更新；不替换旧 `app.quanneng`。
- APK 大小 60,387,574 字节，SHA-256 `add41f53010324e39f9876f1fd38a159ff8c8495b9a73af28c47b322aa180e43`。
- [Android 35 回归](https://github.com/nmm26301-maker/quanneng/actions/runs/40608287566) 全部通过，代码提交 `2ab105ce39c4d3acf807d4150b30d092cd5c69cc`。18 项前端测试、签名守卫、并存/私有状态保留、系统解锁与原有功能检查通过。
- 新增实测：限速与一次连接中断后继续解码、关闭上游后的缓存重播及拖动、实际 VTT 字幕显示、同剧选集/上下集边界、真实播完自动下一集及关闭连播、单视频隐藏选集。
- 回归使用模拟器和合成数据源；真实公网 HLS/DASH/MP4 另有播放检查，不等同于所有源站、机型和真实 Wi-Fi 条件。更多使用范围见 [流畅播放与剧集](STREAMING.md)。

## v0.4.2 发布

- 正式 arm64-v8a APK，应用 ID `app.quanneng.next`、versionName `0.4.2`、versionCode `14`。继续使用固定发布证书，可覆盖更新全能新版 v0.4.0 / v0.4.1。
- APK 大小 60,387,574 字节，SHA-256 `c383bd29a949bff5e700aa5749e0fddc2c687fa0425916f8cc068e2e00710d47`。
- 修复网页常见 HLS MIME 别名被选成普通文件源而触发 3003；加入无后缀/错误 MIME 的实际内容识别。
- 网页只在真实点播后识别当前视频并自动转接；自动保存标题和来源网页，列表显示来源网站。预加载和无操作自动播放不导出。
- 签名恢复仍使用仓库现有加密备份与私下保存的恢复密码，无需生成或替换发布密钥。
- [Android 35 完整回归通过](https://github.com/nmm26301-maker/quanneng/actions/runs/40640542813)，源码/测试提交 `db20ed07dc6f450b13445f4942cf456ce68211a8`；21 项前端测试和固定签名守卫通过。真实 HTTP 无后缀 HLS/DASH、错误后缀 MP4、拒绝 Range 的 HLS/MP4、HTML 伪装诊断、跨域 iframe 实际触摸后只转接所选 Blob、伪造消息不转接、标题/来源持久化及转交原生播放器均通过。原有公网 HLS/DASH/MP4、本地播放、字幕、缓冲缓存、选集、锁定与系统解锁回归通过。
