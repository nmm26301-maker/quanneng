# 全能新版安全备份与迁移验收

最后核对：2026-11-01。此文件只记录备份状态与验收步骤，不保存明文密钥、恢复口令或 API Token。

## 当前已确认

- 正式应用 ID：`app.quanneng.next`；固定发布证书 SHA-256：`bf6f9d9599d42993eb0160efd5d71a070f75d0d5ffc0c0a87bf8bb04d8c8e182`。
- 仓库有加密签名归档：[`signing/QuannengNext-release-signing-backup.encrypted.json`](../signing/QuannengNext-release-signing-backup.encrypted.json)，当前文件约 6 KB；README 声明使用 AES-256-GCM + scrypt。
- 仓库包含恢复脚本：[`android/tools/restore-signing.py`](../android/tools/restore-signing.py)；恢复和构建说明：[安装与签名恢复](INSTALLATION.md)。
- 文档记录解密输出 ZIP 的 SHA-256 为 `03195ef3dc609cb75dc0682e3f88ba8ef173fe9dda7aa0e1645fee2f730950bf`，签名证书指纹为上方固定指纹。
- 仓库忽略 `*.p12`、`*.keystore`、`*signing-backup.zip` 和 `*recovery-password*.json`，避免将明文签名材料和恢复口令误提交。
- 正式版文档记录 v0.4.3 / versionCode 15 的 APK SHA-256 为 `4c5b110bc304c5283dc9df336c6dcee393e56675b8ac93e8b3b21c5b83891c01`，大小 60,391,670 字节；但该 APK 当前没有在 GitHub Releases API 中发现对应的 Release asset。
- 最新功能分支 `feature/post-video-handoff` 有开放 PR [#1：v0.4.9 多语言视频字幕与双指画面缩放](https://github.com/nmm26301-maker/quanneng/pull/1)。截至核查时，main 仍停在 v0.4.3；PR 检查成功不代表已合并、正式发布或真机验收。为避免在签名恢复未验收时继续发布，本台账不将其标记为正式版。

## 仍未完成（不可用文档代替实测）

- [ ] 在所有者实际持有的独立位置找到 `QuannengNext-signing-recovery-password.json` 或恢复口令；它不应放进此仓库。
- [ ] 用该口令在干净目录解密加密归档，确认输出 ZIP 哈希为 `03195ef3dc609cb75dc0682e3f88ba8ef173fe9dda7aa0e1645fee2f730950bf`。
- [ ] 检查归档内 P12 的证书 SHA-256 与 `bf6f9d9599d42993eb0160efd5d71a070f75d0d5ffc0c0a87bf8bb04d8c8e182` 一致；确认能用同一密钥签名。
- [ ] 用原密钥在干净环境构建正式 APK，校验包名、签名证书、版本号与 APK SHA-256；在 Android 上安装并验证覆盖升级。不要生成新密钥替换旧密钥。
- [ ] 将已验证的加密备份另存到第二个独立位置（例如用户控制的加密离线盘/密码管理器附件）；仓库不是唯一副本。
- [ ] 为 v0.4.3 正式 APK 建立长期可下载的 Release asset 或独立备份副本，并保存 SHA-256。Actions artifact 仅作短期 CI 产物，不作为唯一长期备份。
- [ ] API/服务凭据：当前 README 说明全能新版已移除 AI/API 桥接；仍需扫描当前源码、工作流和构建脚本确认没有硬编码凭据。若将来接入服务，应把凭据放在对应平台的 Secret 管理器，不提交明文。
- [ ] 跨账号迁移演练：新账号获得仓库、发布和 Actions 所需最小权限；重新配置必要 Secret；重新构建、签名和安装验证通过。

## 口令与密钥保管规则

加密归档可以存放在仓库，但恢复口令必须独立保管。丢失口令将无法解密备份；把口令、明文 P12 或 keystore 放进仓库（即使是私人仓库）会增加泄漏风险。若凭据曾经公开，应轮换/撤销，而不是只删除文件。

## 结论

加密签名副本与恢复工具已经存在于仓库，但**尚未完成本轮实际解密恢复和证书核验**；因此不能宣称签名备份已完全验收。先完成上面的恢复测试，再合并/发布 v0.4.9 或进行签名发布。