# Aerial Compact / 航拍定制桌面 — v1.0.0

[Source repository / 源码仓库](https://github.com/tigercong2021/aerial-compact)

**Source-only release; no APK attached. / 仅公开源码，不附 APK。**

## 中文

本分支首次正式发布里程碑。**公开版本为 v1.0.0；Android 内部仍为 `1.62-compact-home.9`，versionCode `84`，包名 `app.orchard.tv.compact`。** 基于 Aerial v1.62 的独立定制，不是上游官方发行版或 Apple tvOS。

本次公开源码与本地 V9 保持相同 Kotlin 实现，但省略 3 张弃用影视海报及 QQ 音乐、Kodi 两张可选原图，因为其再分发许可未得到确认。**本地验收 APK 不上传，公开仓库不附 APK。** 自行构建的默认桌面从已安装 App 取图，不包含本机五张自定义图标，不承诺图标资产与本机一致。保留的 QQ/Kodi 专用高清图标替换接口仍依赖省略图片，调用时会报告资源不可用，不提供自动缺图回退；海报不支持。

### 本版内容

- 壁纸为主的五卡小底栏、隐藏名称、轻薄底板与遥控焦点反馈；时间天气左上，设置右上，24 dp 顶角视觉边距。
- 保留满幅应用图标、原桌面入口和系统任务页行为；不包含影视海报、观看历史读取或跨应用续播。
- 随包提供 114 条在线航拍的目录，目录内随机轮播，不是每日新增影片；Bing `daily:latest` 在网络与源站可用时跟随最新日图，固定日期照片除外。
- 修复已测盒子软件重启后系统名单／屏保设置被固件重置的问题：设备所有者同意授予 WSS 后，代码仅维护 `enable_running_backgroud_components`、`enable_launcher_components`、`screensaver_components`。
- 返回服务维护使用三个精确 URI 观察器，每会话最多四次修复批次，失败和竞争也计数；主页创建另有一次维护。不新增开机接收器、后台服务、轮询或电脑守护。

### 本地 V9 实现验证

下列构建、7 项测试、重启及唤醒结果来自本地 V9 验证构建，针对同一 Kotlin 实现；不是省略可选媒体的公开构建已经通过实机验收的声明。公开构建需分别验证可构建性与设备效果。

在腾讯极光盒子 3 Pro（A4091 / newton，Android 9）上：

- 软件重启后自动恢复三键、默认 HOME 与定制桌面；取证前未按 HOME、未人工补值，用户确认动态航拍。
- 从运行中的系统航拍屏保进入 Asleep / OFF，物理遥控唤醒为 Awake / ON；未先按 HOME，用户确认定制桌面与动态航拍。
- 安装、重启及唤醒前后，所选设置、HOME、应用偏好及五张图标哈希保持；原语音和返回服务保留。
- QQ 音乐正常退出回定制桌面；主动进入腾讯原桌面可停留，HOME 返回；长按 HOME 进入系统最近任务页，BACK 返回。当前版本未重复测试一键清理。
- 构建、release lint、7 项单元测试通过，失败及跳过为 0。独立源码审查完成；本地清单仅新增 WSS，服务／receiver 不变，本地 V9 签名与本地 V8 一致；此私钥不发布。

此前 V8 的重启设置丢失与首轮唤醒失败记录保留，本版验收不追溯改写历史结论。其他机型、长期运行、断电冷启动及未来固件升级未验证；设备上的撤权停止行为未单独实测。

### 安装、签名与关闭维护

本仓库不附 APK，请自行构建并签名 compactRelease；不要运行上游 `tools/install.sh` 等 standard/guard 部署或恢复脚本。安装、启用返回服务、选择默认 HOME 及授予 WSS 均须设备所有者同意。WSS 是较广权限，三键限制属于代码实现，并非 Android 权限本身只允许三键；grant 后应重连已授权的返回服务并保留原语音服务。

签名私钥、密码和设备配置不发布。他人自签 APK 无法直接覆盖不同签名的现有安装；普通覆盖降级也不保证可行。完整构建、安装和恢复步骤见 README。

停止新增维护应先撤销 WSS，再重连／重开核验。仅关闭返回服务停止持续维护，但权限仍在时主页重新创建可能单次维护；撤权不自动恢复已经写入的系统值。原值按本设备备份恢复，保留其他服务与名单项。

### 版本与本地验证追溯

| 项目 | 值 |
| --- | --- |
| 发布里程碑 | `v1.0.0` |
| Android versionName | `1.62-compact-home.9` |
| Android versionCode | `84` |
| Application ID | `app.orchard.tv.compact` |
| 本地验证 APK SHA-256（不发布） | `871cec105102827d64530c4d1b21ac68d1526f6348b6906e0172a69d4fbd3cfd` |

表中摘要仅对应不公开的本地验收 APK，不是公开下载文件或自行构建产物的校验值。

代码源自 [Aerial](https://github.com/pengchujin/aerial-tv)，遵循 MIT，保留上游版权与许可文本；第三方媒体、标志与商标权利不因代码许可而转让。

---

## English

The first release milestone for this fork. **The public release is v1.0.0; Android remains `1.62-compact-home.9`, versionCode `84`, application ID `app.orchard.tv.compact`.** This is an independent Aerial v1.62 customization, not an official upstream release or Apple tvOS.

The public source retains the same Kotlin implementation as local V9 but omits three abandoned film posters and the optional QQ Music and Kodi source images because redistribution permission was not established. **The local validation APK is not uploaded, and no APK is attached to this source release.** Default desktop icons in your own build come from installed apps; the five local custom tiles are not included, and asset parity is not promised. The retained QQ/Kodi high-resolution icon-replacement path still depends on those omitted files and reports them unavailable when invoked, without automatic fallback. Poster mode is unsupported.

### Included

- Wallpaper-first five-tile compact row with hidden labels, a subtle plate, and remote focus feedback. Clock/weather at the upper left and settings at the upper right, with 24 dp visual margins.
- Large app artwork, explicit access to the original launcher, and native recent-task navigation. No film-poster focus mode, viewing-history extraction, or cross-app playback resumption.
- A bundled catalog of 114 online aerial films, shuffled within that catalog rather than expanded daily. Bing `daily:latest` follows new daily images when the network and source are available; a fixed dated image does not.
- A fix for the tested box resetting launcher/background eligibility and the active screensaver after software reboot. With the owner’s WSS consent, code maintains only `enable_running_backgroud_components`, `enable_launcher_components`, and `screensaver_components`.
- Three exact-URI observers with at most four repair batches per return-service session, including failures and races. Home Activity creation has a separate one-shot pass. No added boot receiver, background service, polling loop, or computer watchdog.

### Local V9 implementation validation

The build, seven tests, reboot, and wake results below come from the local V9 validation build using the same Kotlin implementation. They do not establish on-device validation of the public build with optional media omitted. Public buildability and device behavior must be checked separately.

On Tencent Aurora Box 3 Pro, A4091 / newton, Android 9:

- Software reboot automatically restored the three settings, default HOME, and custom foreground. No manual HOME or settings repair preceded capture; the user confirmed moving aerial video.
- A running system aerial screensaver entered Asleep / OFF, then physical-remote wake returned to Awake / ON without first pressing HOME. The user confirmed the custom home and moving aerial video.
- Sampled settings, HOME, preferences, and five tile hashes matched before and after installation, reboot, and wake. Original voice and return services remained present.
- QQ Music normal exit returned to the custom home. Explicit Tencent entry remained open until HOME; long HOME opened native recents and BACK returned. One-click cleanup was not retested in this version.
- Build, release lint, and all seven unit tests passed, with zero failures or skips. Independent source review completed. Only WSS was added to the local manifest; services/receivers match, and local V9 shares the local V8 signing certificate. Its private key is not published.

Earlier V8 settings-loss and first-wake failures remain part of the historical record. This release does not rewrite them. Other devices, long-term operation, power-disconnection cold boot, and future firmware updates are unverified. On-device permission-revocation stopping was not separately tested.

### Installation, signing, and stopping maintenance

No APK is attached. Build and sign compactRelease yourself. Do not run upstream `tools/install.sh` or other standard/guard deployment or restore scripts. Installation, return-service enablement, default-HOME selection, and WSS grant each require the device owner’s consent. WSS is broad; the three-key limit belongs to the implementation, not the Android permission. After granting it, reconnect the authorized return service while preserving the voice service.

Signing private keys, passwords, and device configuration are not published. A self-signed APK cannot update an existing installation signed with a different key; a normal in-place downgrade is not guaranteed. See the README for building, installation, and restoration.

To stop the new maintenance, revoke WSS first, then reconnect/reopen and verify. Disabling only the return service stops its ongoing session, but with WSS still granted a newly created home Activity can run one pass. Revocation does not restore already-written values. Use this device’s saved originals and preserve other services and list entries.

### Version and local validation provenance

| Item | Value |
| --- | --- |
| Release milestone | `v1.0.0` |
| Android versionName | `1.62-compact-home.9` |
| Android versionCode | `84` |
| Application ID | `app.orchard.tv.compact` |
| Local validation APK SHA-256 (not distributed) | `871cec105102827d64530c4d1b21ac68d1526f6348b6906e0172a69d4fbd3cfd` |

This checksum identifies only the unpublished local validation APK, not a public download or a self-built artifact.

Based on [Aerial](https://github.com/pengchujin/aerial-tv), under MIT with upstream copyright and license notices retained. The code license does not transfer rights to third-party media, logos, or trademarks.
