# Public source build validation / 公开源码构建验证

Validation date / 验证日期：2026-10-28

Release milestone / 发布里程碑：`v1.0.0`  
Android version / Android 内部版本：`1.62-compact-home.9` / code `84`

## 中文

**已排除试用图片的公开源码独立构建通过；7 项设置维护测试全部通过。** 本次验证对象是公开源码导出，而非此前保留第三方媒体的本地 V9 APK。

| 检查 | 结果 |
| --- | --- |
| `:app:assembleCompactRelease` | 成功，生成 compactRelease APK |
| `:app:testCompactReleaseUnitTest` 的 `CompactSecureSettingsTest` | 7 项，通过 7，失败 0，错误 0，跳过 0 |
| XML 报告复核 | suite 计数与 7 个 testcase 一致，无 failure / error / skipped 节点 |
| 构建自带 `lintVitalCompactRelease` | 执行成功；不将此当作本次另行执行完整 `lintCompactRelease` 的证明 |
| 公开构建 APK 装机 | **未执行** |
| APK 分发 | **本次源码发布不提供 APK** |

检查依据为构建日志中的 `BUILD SUCCESSFUL`、实际输出文件，以及生成的测试报告 `app/build/test-results/testCompactReleaseUnitTest/TEST-app.orchard.tv.guard.CompactSecureSettingsTest.xml`。该测试套件报告运行时间为 2.509 秒。构建存在编译警告，成功不等于无警告；本记录也不表示所有项目测试均已运行。

公开源码省略三张弃用影视海报，以及 QQ 音乐和 Kodi 两张可选原图。省略这些资源后能够编译，**不代表所有依赖可选图片的运行路径都可用**：默认桌面可从已安装 App 取图，但保留的 QQ/Kodi 专用高清替换接口仍依赖省略资源，详见 [README](README.md)。

电视上的软件重启、系统航拍屏保待机唤醒和导航验收，对应此前的本地 V9 验证构建；不是本次公开源码构建 APK 的装机测试。公开构建未替换电视应用，也不承诺与本地五张自定义图标资产一致。长期运行、断电冷启动及未来固件升级后的表现未因此获得验证。

## Reproduction / 复现

Use JDK 17, Android SDK Platform 36, and the included Gradle Wrapper from the repository root. / 使用 JDK 17、Android SDK Platform 36，并在仓库根目录运行自带 Gradle Wrapper：

```sh
./gradlew :app:assembleCompactRelease :app:testCompactReleaseUnitTest --tests "app.orchard.tv.guard.CompactSecureSettingsTest"
```

Generated artifact / 生成文件：`app/build/outputs/apk/compact/release/app-compact-release.apk`。This is a local build output, not an attached release asset. / 这是本地构建输出，不是本次附带发布的下载资产。

Test cases verified / 已复核测试用例：

- `missingOemListsAreNotCreated`
- `deniedPermissionCreatesNoObserverAndChangesNothing`
- `closeRemovesAllThreeObserversBeforeTheQueuedRepair`
- `repairPreservesListBytesAndLeavesOtherSettingsAndStockVisitAlone`
- `revocationDetectedOnAnExistingEventClosesTheSessionWithoutWriting`
- `exactExistingPackageKeepsDuplicatesAndSeparatorsUnchanged`
- `wssDoesNotEnableLegacyXiaomiGlobalBrightnessWrites`

## English

**The public source export, with trial images excluded, built independently and passed all seven settings-maintenance tests.** This validation used the public source export, not the earlier local V9 APK that retained third-party media.

| Check | Result |
| --- | --- |
| `:app:assembleCompactRelease` | Successful; compactRelease APK generated |
| `CompactSecureSettingsTest` under `:app:testCompactReleaseUnitTest` | 7 tests, 7 passed, 0 failures, 0 errors, 0 skipped |
| XML report inspection | Suite totals match seven testcase elements; no failure, error, or skipped elements |
| Build-integrated `lintVitalCompactRelease` | Completed successfully; this is not evidence that the full `lintCompactRelease` task was separately run in this validation |
| Installing this public-build APK on the TV | **Not performed** |
| APK distribution | **No APK is attached to this source release** |

Evidence consists of `BUILD SUCCESSFUL` in the build log, the generated output file, and `app/build/test-results/testCompactReleaseUnitTest/TEST-app.orchard.tv.guard.CompactSecureSettingsTest.xml`. The suite reports 2.509 seconds. Compiler warnings were present: a successful build is not a warning-free claim. This record does not claim that every project test was executed.

The public source omits three abandoned film posters and the optional QQ Music and Kodi source images. Successful compilation without them **does not establish that every optional-image-dependent runtime path works**. Default desktop icons can come from installed apps, but the retained QQ/Kodi high-resolution replacement path still depends on omitted files; see the [README](README.md).

The TV’s software-reboot, system-aerial standby/wake, and navigation acceptance belongs to the earlier local V9 validation build. It is not an installation test of this public-source build. The generated APK did not replace the TV application, and parity with the five local custom tiles is not promised. This check does not validate long-term operation, power-disconnection cold boot, or future firmware updates.
