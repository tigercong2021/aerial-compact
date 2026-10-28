# Aerial Compact / 航拍定制桌面

[中文](#中文) · [English](#english) · [Repository / 源码仓库](https://github.com/tigercong2021/aerial-compact)

A compact, wallpaper-first Android TV home screen based on [Aerial](https://github.com/pengchujin/aerial-tv). / 基于 Aerial 的小图标 Android TV 桌面，以壁纸与航拍为主。

**Source-only release — no APK is attached. / 本次仅公开源码，不附 APK。**

| Release / 发布里程碑 | Android version / Android 内部版本 | versionCode | Application ID / 包名 |
| --- | --- | --- | --- |
| **v1.0.0** | `1.62-compact-home.9` | `84` | `app.orchard.tv.compact` |

**v1.0.0 is this fork’s first release milestone; it does not rename the Android version. / v1.0.0 是本分支首次正式发布里程碑，不改变 Android 内部版本号。**

## 中文

### 这是什么

Aerial Compact / 航拍定制桌面是基于 Aerial v1.62 的独立定制分支，参考 Apple TV / tvOS 的简洁界面。它不是 Apple tvOS，也不是 Apple、腾讯或 Aerial 上游的官方发行版。定制包名与上游 `app.orchard.tv` 不同，设置分开保存。

本地 V9 验证构建已在**腾讯极光盒子 3 Pro，A4091 / newton，Android 9** 上完成本轮软件重启、航拍屏保待机后的物理遥控唤醒及导航测试。公开源码保留相同 Kotlin 实现，但省略下述可选第三方媒体，不能把本地验证构建的素材与画面视为公开构建完全相同。其他机型尚未验证。Android 清单声明最低 API 21，不代表全部 Android 5.0 及以上设备已兼容。

### 本次公开范围

本仓库发布源码、双语说明与 v1.0.0 标签，**不提供预编译 APK，也不上传本地验收 APK**。没有取得再分发许可的 3 张弃用影视海报、QQ 音乐原图及 Kodi 原图不进入公开包；“官方来源”本身不等于获准再分发。

公开构建的默认桌面图标来自设备上已安装的 App，因此外观与分辨率取决于各 App 版本。它不附带本机已经保存的五张自定义图标，也不承诺还原本机的图标资产。海报功能不受支持。保留的 Kotlin 图标替换接口对 QQ 音乐／Kodi 的专用高清处理仍引用被省略的可选图片；直接调用该处理会报资源不可用，不能将它理解为缺图时自动回退。默认桌面原生取图不依赖这两张额外图片。

### 界面与使用

- 壁纸为主，默认只显示一排五个小图标；宽高约为上游的 71.7%，应用栏靠近底部，名称隐藏，保留轻薄底板与遥控焦点反馈。更多应用仍可向下浏览。
- 时间与天气在左上，设置入口在右上；顶角视觉边距为 24 dp。应用栏相对早期定制版再下移 20 dp，图标尺寸和间距不变。
- 满幅应用图标保留原图比例；实际清晰度取决于源图。1280×720 的输出卡片不意味着所有源图都有这个分辨率。
- 原腾讯内容桌面仍可作为应用入口主动打开。已验证主动进入后可停留，按 HOME 回到定制桌面；没有另装或虚构独立腾讯视频包。
- 已验证 QQ 音乐正常点击退出回到定制桌面。长按 HOME 仍进入盒子自带最近任务页，BACK 返回定制桌面。本版没有重复验收“一键清理”。
- 不包含聚焦影视海报、读取观看历史或跨应用续播功能。

### 航拍与每日一图

源码随附的是 **114 条航拍影片的目录**，播放来源为上游目录中的 Apple CDN，影片并非全部打包在 APK 内。选择航拍轮播时，从目录中随机循环；不会每天自动添加新航拍素材。播放依赖网络、源站和设备解码能力，本次没有逐条验证全部 114 条影片。

Bing“每日一图”的 `daily:latest` 会跟随最新图片。程序保存最近 8 张，定期检查，并在日期变化或网络恢复后重试；必须有可用网络和源站响应。固定选择某一天的照片不会自动跟随最新图片。这是应用内行为，无需电脑端定时任务。

### 权限与三项设置维护

部分固件会在重启时删除第三方桌面候选、后台运行资格或当前屏保组件。此分支允许设备所有者**明确同意后**，为定制包授予 `android.permission.WRITE_SECURE_SETTINGS`（WSS），配合已有返回辅助服务恢复以下三个 Secure 键：

| 键 | 本版代码的操作 |
| --- | --- |
| `enable_running_backgroud_components` | 保留现有列表的每个条目，仅在缺少精确 `app.orchard.tv.compact` 项时追加；缺失或空白时跳过。`backgroud` 是固件原拼写。 |
| `enable_launcher_components` | 同样只追加本应用精确项，保留原列表；缺失或空白时跳过。 |
| `screensaver_components` | 设为 `app.orchard.tv.compact/app.orchard.tv.dream.AerialDream`。 |

**WSS 本身是较广的 Android 权限，系统并没有将它限制为这三个键；三键边界依靠本版代码实现与审查。** 应用不能自行授予权限。维护不写默认 HOME、辅助功能登记、屏保默认组件、屏保总开关、ADB 或其他设置，也不会为维护而启动 Activity、增加开机接收器、新服务、轮询或电脑守护。返回辅助服务只使用事件包名，不读取屏幕文字或界面节点。

主页创建时，有 WSS 才执行一次维护。返回服务连接时，有 WSS 才建立会话，只监听三个精确设置 URI。会话通知至少合并 250 ms，最多四次实际修复批次；读写失败和竞争也消耗预算，冷却为 1、5、15、30 秒。没有差异就不写，预算耗尽而仍有差异则停止。主页的一次维护与会话预算分开，不能把有限维护理解为永久保活。

### 安装与启用

只在本人管理的设备上操作。先记录自己的默认 HOME、辅助服务及相关设置原值；不同固件的原值不能照抄他人的。安装、选择默认桌面、开启返回辅助服务和授予 WSS 是分别需要设备所有者同意的动作。

1. 本仓库不附 APK。先按下文自行构建并签名 **compactRelease**，核对包名、签名与自己的构建摘要后安装；首次打开先检查界面与遥控操作。
2. 在系统设置中，自行选择是否启用定制版的返回辅助服务；保留已有语音及其他辅助服务。需要默认桌面时，由本人选择默认 HOME，维护代码不会替你设置首选 HOME。
3. 只有理解并同意上述三键维护时，才使用已授权的 ADB 连接授予 WSS：

```sh
adb shell pm grant app.orchard.tv.compact android.permission.WRITE_SECURE_SETTINGS
```

4. 授权后重新连接已启用的返回服务，例如在系统设置中关闭再开启**该服务本身**，保留其他服务；或重新启动应用进程并确认服务再次连接。grant 不会自动给已连接的服务创建观察会话，主页的一次恢复也不能代替会话核验。
5. 在自己的设备上核验原桌面入口、HOME、普通 App 退出、屏保、软件重启与遥控唤醒。其他固件可能没有这些名单键，可能拒绝授权或后台启动，不能直接沿用本机结论。

**不要运行上游 `tools/install.sh`、repair 或 restore 脚本来部署此分支。** 它们面向上游包名／standard 或 guard 流程，可能安装或配置错误变体。也不要为此停用原厂桌面或其他系统应用。

### 构建与签名

环境：JDK 17、Android SDK Platform 36；使用项目 Gradle Wrapper（8.14.3）。在项目根目录执行：

```sh
./gradlew :app:assembleCompactRelease
./gradlew :app:lintCompactRelease
./gradlew :app:testCompactReleaseUnitTest --tests "app.orchard.tv.guard.CompactSecureSettingsTest"
```

输出：`app/build/outputs/apk/compact/release/app-compact-release.apk`。安装自己构建的 APK 可使用：

```sh
adb install -r app/build/outputs/apk/compact/release/app-compact-release.apk
```

本分支不发布签名私钥、密码或设备配置。当前 Gradle 配置读取本地 `local/signing.properties`；缺少维护者签名配置时会回退到本机 debug 签名。**他人自行签名的 APK 不能直接覆盖现有不同签名的安装。** 为自己的安装保留同一私钥，避免以后更新失败；卸载再装会丢失应用设置，不作为默认更新方案。公开构建省略了可选媒体且使用你自己的签名，不应期待与下方仅作本地验证追溯的 APK 摘要一致。

### 停止维护与恢复

要停止新增的自动设置写入，先撤销 WSS，再重连返回服务或重新打开应用并核验：

```sh
adb shell pm revoke app.orchard.tv.compact android.permission.WRITE_SECURE_SETTINGS
```

撤权后写入会被拒绝，代码在下一入口、窗口事件或设置回调发现撤权时关闭会话；完全静止时观察器不一定立即注销。仅关闭返回辅助服务会停止其持续维护，但若 WSS 仍保留，之后重新创建主页仍可能执行一次维护。

撤权不自动撤销之前写入的设置。如需完整回退，使用本设备保存的原值：仅移除 compact 的精确名单项和返回服务，保留原语音／其他服务，分别恢复屏保组件与默认组件，再核验 HOME。不要假定两项屏保原值相同，也不要把指定腾讯默认为“恢复了所有原状态”。本分支 code 84 高于早期 code 83，不承诺普通覆盖即可降级。

### 本地 V9 实现验证与公开构建边界

以下结果来自本地 V9 验证构建，对应公开源码中的同一 Kotlin 实现；它们不是省略可选媒体后公开构建的实机验收声明。排除试用图片后的公开源码已独立构建成功，7 项设置维护测试全部通过、失败／错误／跳过均为 0，但生成的 APK 未装机也不发布；详见 [公开源码构建验证](BUILD_VALIDATION.md)。

- 本地 V9 构建、release lint、7 项设置维护单元测试通过，失败／跳过为 0；独立源码审查完成。
- 本地 V9 相对本地 V8 只新增 WSS 声明，服务和 receiver 集合不变，二者签名相同；此签名私钥不公开。
- A4091 / Android 9 上，软件重启后自动修复三键；无需手动 HOME 或补值，默认 HOME、前台、权限、服务和所选设置正确。
- 从实际运行的系统 AerialDream 进入 Asleep / OFF 后，物理遥控唤醒到 Awake / ON；未先按 HOME，用户确认定制桌面和动态航拍正常。
- 安装、重启、唤醒前后，采样偏好和五张图标哈希一致；原语音服务保留，QQ 退出、主动腾讯入口／HOME、长 HOME／BACK 均通过。
- 只代表本轮软件重启、屏保待机唤醒及导航测试。**长期运行、断电冷启动、未来固件升级，以及设备上的撤权停止测试没有完成验证。** 不承诺永久绕过固件限制。

仅作验证追溯的**本地 V9 APK** SHA-256（该 APK 不发布，不是公开下载文件或自行构建产物的校验值）：

```text
871cec105102827d64530c4d1b21ac68d1526f6348b6906e0172a69d4fbd3cfd
```

### 来源与许可

基于 [pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv) 的 [固定提交 `bb934a7`](https://github.com/pengchujin/aerial-tv/tree/bb934a7a3ce7f151df97b6637317adcacfd686fd)，上游 v1.62。代码遵循 [MIT License](LICENSE)，保留上游 `Copyright (c) 2026 pengchujin` 及许可文本。

第三方应用标志、商标、图片和远程航拍视频仍属于各自权利人；代码的 MIT 许可不为这些素材另行授予再分发权。Apple TV / tvOS 仅用于说明界面参考，不表示关联或背书。

---

## English

### About

Aerial Compact is an independent customization of Aerial v1.62 with an Apple TV / tvOS-inspired interface. It is not Apple tvOS or an official release from Apple, Tencent, or the upstream Aerial project. Its application ID, `app.orchard.tv.compact`, keeps its settings separate from upstream `app.orchard.tv`.

The local V9 validation build was tested on a **Tencent Aurora Box 3 Pro, A4091 / newton, Android 9**. The public source retains the same Kotlin implementation but omits the optional third-party media described below. Its assets and visual output must not be assumed identical to that local build. Other devices are unverified. The manifest declares API 21 as the minimum; that is not a compatibility claim for every Android 5.0+ device.

### What is published

This repository contains source, bilingual documentation, and the v1.0.0 tag. **No prebuilt APK or local validation APK is distributed.** Three abandoned film posters and the optional QQ Music and Kodi source images are omitted because redistribution permission was not established. Official provenance alone is not redistribution permission.

Default desktop icons in a public build come from apps installed on the device, so appearance and resolution depend on those app versions. The five saved custom tiles from the local device are not included, and visual asset parity is not promised. Poster mode is unsupported. The retained Kotlin icon-replacement endpoint still references the omitted optional images for its QQ Music/Kodi high-resolution path; invoking that path reports the resource as unavailable rather than silently falling back. Default desktop native-icon loading does not depend on those two extra images.

### Interface and behavior

- Wallpaper-first layout with one visible row of five smaller app tiles by default. Tiles are about 71.7% of the upstream width and height, placed near the bottom with hidden app labels, a subtle plate, and remote focus feedback. Additional apps remain accessible by scrolling down.
- Clock and weather at the upper left; settings at the upper right, with 24 dp visual margins. The row is another 20 dp lower than earlier custom builds, without changing tile size or spacing.
- Large app artwork preserves the original aspect ratio. A 1280×720 output card does not imply that its source artwork has that resolution.
- The original Tencent home/content app remains explicitly accessible. Selecting it allows it to stay open; HOME returns to the custom home. No separate Tencent Video package is installed or invented.
- QQ Music’s normal exit returns to the custom home. Long HOME opens the box’s native recent-task screen, and BACK returns. One-click memory cleanup was not retested in this release.
- No focus-based film posters, viewing-history extraction, or cross-app playback resumption.

### Aerial films and Bing daily images

The source includes a **catalog of 114 aerial films**, referencing Apple CDN URLs from the upstream catalog; it does not bundle every film. Aerial playback shuffles through this catalog rather than adding new films every day. Availability depends on the network, CDN, and device decoder. Not every catalog entry was individually tested.

Bing’s `daily:latest` selection follows the newest daily image. The app keeps the latest eight images and checks periodically, with retries around date changes or restored connectivity. Network and source availability are required. Selecting a particular dated image keeps that image rather than following the latest one. No computer-side scheduled task is needed.

### Permission and three-setting maintenance

Some firmware resets third-party launcher eligibility, background eligibility, or the active screensaver during reboot. With the device owner’s **explicit consent**, the custom package can be granted `android.permission.WRITE_SECURE_SETTINGS` (WSS). The current code restricts its maintenance to three Secure keys:

| Key | Implemented operation |
| --- | --- |
| `enable_running_backgroud_components` | Preserve every existing list entry and append the exact `app.orchard.tv.compact` token only if missing. Skip absent or blank lists. The firmware’s `backgroud` spelling is intentional. |
| `enable_launcher_components` | The same exact-token append, preserving the existing list and skipping absent or blank values. |
| `screensaver_components` | Set to `app.orchard.tv.compact/app.orchard.tv.dream.AerialDream`. |

**WSS itself is a broad Android permission. Android does not restrict it to these three keys; that boundary is enforced by this implementation and its review.** The app cannot grant itself permission. Maintenance does not set the preferred HOME, modify accessibility registrations, the default screensaver component, screensaver enablement, ADB, or other settings. It does not launch an Activity or add a boot receiver, service, polling loop, or computer watchdog. The return accessibility service uses event package names, without reading screen text or UI nodes.

With WSS, creation of the home Activity performs one maintenance pass. Connection of the return service creates a session observing only the three exact settings URIs. Notifications are coalesced for at least 250 ms. A session allows at most four repair batches, including read/write failures and races, with 1, 5, 15, and 30-second cooldowns. No difference means no write; unresolved differences after the budget stop the session. The Activity’s one-shot pass is separate from the session budget. This is bounded maintenance, not a permanent keep-alive guarantee.

### Installation and opt-in setup

Use a device you administer. First record that device’s original HOME, accessibility services, and relevant settings; do not copy another device’s original values. Installing the app, selecting a default home, enabling its return accessibility service, and granting WSS are separate choices requiring the owner’s consent.

1. No APK is attached. Build and sign **compactRelease** yourself as described below, then check its application ID, signature, and your own build checksum before installation. Open it to check appearance and remote behavior first.
2. If wanted, enable only the custom return service through system settings, preserving voice and other accessibility services. Select the default HOME yourself where needed; maintenance does not select it for you.
3. Only after accepting the maintenance scope above, use an authorized ADB connection to grant WSS:

```sh
adb shell pm grant app.orchard.tv.compact android.permission.WRITE_SECURE_SETTINGS
```

4. Reconnect the enabled return service after granting permission, for example by toggling **that service alone** in system settings while preserving others, or restart the app process and verify that the service reconnects. Granting permission does not create observers in a service that was already connected without it. An Activity’s one-shot repair is not proof of an observer session.
5. Verify the original launcher entry, HOME, normal app exit, screensaver, software reboot, and physical remote wake on your device. Other firmware may lack these list keys or reject permission/background launches.

**Do not deploy this fork with upstream `tools/install.sh`, repair, or restore scripts.** They target upstream package names and standard/guard workflows and may install or configure the wrong variant. Do not disable the stock launcher or other system apps to make this work.

### Building and signing

Use JDK 17, Android SDK Platform 36, and the included Gradle Wrapper (8.14.3). From the project root:

```sh
./gradlew :app:assembleCompactRelease
./gradlew :app:lintCompactRelease
./gradlew :app:testCompactReleaseUnitTest --tests "app.orchard.tv.guard.CompactSecureSettingsTest"
```

Output: `app/build/outputs/apk/compact/release/app-compact-release.apk`. To install your own build:

```sh
adb install -r app/build/outputs/apk/compact/release/app-compact-release.apk
```

Signing private keys, passwords, and device configuration are not published. The current Gradle configuration reads local `local/signing.properties` and falls back to the build machine’s debug key if the maintainer’s signing configuration is absent. **An independently signed APK cannot directly update an existing installation signed with a different key.** Keep the same private key for your own future updates. Uninstalling loses app settings and is not the default update procedure. A public-source build omits optional media and uses your own signature; it is not expected to match the local validation APK’s provenance checksum below.

### Stopping maintenance and restoring settings

To stop the new automatic settings writes, revoke WSS first, then reconnect the return service or reopen the app and verify:

```sh
adb shell pm revoke app.orchard.tv.compact android.permission.WRITE_SECURE_SETTINGS
```

After revocation, writes are denied. The code closes its session when the next entry point, window event, or settings callback observes the revoked permission. An idle observer may not unregister immediately. Disabling the return service stops its ongoing maintenance, but while WSS remains granted a later home Activity creation can still perform its one-shot pass.

Revocation does not undo previous writes. For a full rollback, use your device’s saved original values: remove only this package’s exact list entries and return service, preserve voice and other services, restore active and default screensaver components separately, and verify HOME. Do not assume both screensaver fields originally matched or that choosing Tencent as default restores every previous state. Version code 84 is newer than earlier code 83 builds; a normal in-place downgrade is not promised.

### Local V9 validation and public-build boundaries

The results below are from the local V9 validation build and cover the same Kotlin implementation. They are not an on-device validation claim for a public build with optional media omitted. The public source with trial images excluded built independently and passed all seven settings-maintenance tests with zero failures, errors, or skips; its generated APK was not installed or distributed—see [public source build validation](BUILD_VALIDATION.md).

- The local V9 build, release lint, and all seven settings-maintenance unit tests passed, with zero failures or skips; independent source review completed.
- Between local V8 and local V9, WSS is the only added permission. Services and receivers are unchanged, and those two local builds share a signing certificate. Its private key is not published.
- On A4091 / Android 9, software reboot automatically reconciled the three settings. No manual HOME or settings repair was used before checking foreground, default HOME, permissions, service, and sampled settings.
- A running system AerialDream entered Asleep / OFF, followed by physical-remote wake to Awake / ON without first pressing HOME. The user confirmed the custom home and moving aerial video.
- Sampled preferences and five tile hashes matched before and after installation, reboot, and wake. Voice service was preserved; QQ exit, explicit Tencent entry/HOME, and long-HOME/BACK passed.
- Results cover this round of software reboot, screensaver standby/wake, and navigation only. **Long-term operation, power-disconnection cold boot, future firmware updates, and on-device permission-revocation stop behavior were not validated.** No permanent firmware-bypass guarantee is made.

Local V9 validation APK SHA-256, **for provenance only**. This APK is not distributed; the value does not identify a public download or your own build:

```text
871cec105102827d64530c4d1b21ac68d1526f6348b6906e0172a69d4fbd3cfd
```

### Upstream and licensing

Based on [pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv), upstream v1.62, at [commit `bb934a7`](https://github.com/pengchujin/aerial-tv/tree/bb934a7a3ce7f151df97b6637317adcacfd686fd). Code is provided under the [MIT License](LICENSE), preserving the upstream `Copyright (c) 2026 pengchujin` notice and license text.

Third-party app logos, trademarks, images, and remote aerial films remain subject to their respective rights. The code’s MIT license does not separately grant redistribution rights to those assets. Apple TV / tvOS describes an interface reference, not affiliation or endorsement.
