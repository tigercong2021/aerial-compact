# Source provenance / 源码来源

- Public milestone / 公开里程碑: **v1.0.0**.
- Upstream / 上游: https://github.com/pengchujin/aerial-tv
- Upstream source commit / 固定来源提交: `bb934a7a3ce7f151df97b6637317adcacfd686fd`.
- Upstream source archive SHA-256 / 原始来源包: `16de08fff99fdbf276a4e16e015b0c59f3779cd7acb3a635f6220b31ca6e8b16`.
- Implementation / 本地已验实现: `1.62-compact-home.9`, versionCode **84**, package `app.orchard.tv.compact`.
- Tested local APK SHA-256 / 已验本地 APK（不随本次分发）: `871cec105102827d64530c4d1b21ac68d1526f6348b6906e0172a69d4fbd3cfd`.

The exported build files and Kotlin/Java/XML/resources are copied from the frozen local V9 implementation. The public snapshot excludes optional locally used third-party images, private configuration, device records, signing keys, tool installations, the upstream deployment website/scripts, and household-specific documentation. Public documentation and ignore rules are newly written for this fork. The Lite module is retained only because the unchanged Gradle settings include it; this release validates the compact app target only.

公开构建文件与代码/资源取自冻结的本地 V9 实现；省略可选第三方图片、私有配置、设备记录、签名密钥、工具安装、上游部署网站/脚本及家庭设备专用文档。本分支重新编写了公开文档和忽略规则。Lite 模块仅为保留原 Gradle 项目结构而附带，本次只验证 compact 目标。

The local-device test results apply to the original V9 installation. The public source compilation is checked separately; its APK is not claimed to be byte-identical to that tested local APK. Rebuilders use their own signing key and supply any optional media only when they have the required rights.

实机测试对应原 V9 安装，公开源码的构建另行验证；不宣称公开构建与本地 APK 字节相同。自行构建者使用自己的签名，按权利条件自行提供可选素材。
