# 全能新版安装与签名恢复

v0.4.0 安装后显示“全能新版”，独立应用 ID 为 `app.quanneng.next`，可与旧版 `app.quanneng` 共存。请保留旧版安装和数据，保险箱及私有记录继续在旧版使用；普通视频可在新版重新选择。本版包含 v0.3.2 的透明横屏控制、按秒拖动、防误触锁定、按需显示的时钟和系统解锁。

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
