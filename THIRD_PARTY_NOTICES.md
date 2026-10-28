# Third-party notices / 第三方说明

## Aerial upstream / 上游软件

This customization is derived from [pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv), commit `bb934a7a3ce7f151df97b6637317adcacfd686fd` (v1.62 source snapshot). Copyright (c) 2026 pengchujin. The original MIT license is preserved in [LICENSE](LICENSE).

本项目基于上述 Aerial 固定源码快照，保留原作者署名及 MIT 许可证。这里的 v1.0.0 是本定制分支的发布编号，不是上游 Aerial 的版本编号。

## Inter font / Inter 字体

The bundled Inter font is accompanied by its original [SIL Open Font License](FONT-LICENSE-OFL.txt). The software MIT license does not replace this font license.

内置 Inter 字体保留其原始 OFL 许可；软件 MIT 许可不替代字体许可。

## City data / 城市数据

The city table is adapted from [GeoNames](https://www.geonames.org/export/) by upstream Aerial and retains its CC BY 4.0 attribution header. See [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/). This fork exports that table unchanged.

城市表由上游 Aerial 基于 GeoNames 整理，保留 CC BY 4.0 署名头；本分支原样导出该表，不以软件 MIT 许可替代数据许可。

## Online data and media / 在线数据与媒体

The code includes a city lookup table and online media references inherited from Aerial. Weather is obtained from Open-Meteo; aerial videos are streamed from their respective providers, including Apple's public aerial endpoints; Bing images are retrieved online. Those services, data, imagery and trademarks are not relicensed under this repository's MIT license. Availability and use remain subject to their respective sources and terms. No aerial-video or Bing-image files are included in this repository.

代码保留 Aerial 的城市检索表和在线资源索引。天气来自 Open-Meteo；航拍影片从对应来源（包括 Apple 航拍地址）在线播放；Bing 图片在线获取。相关服务、数据、图像和商标不因本仓库软件采用 MIT 而改变其原有权利与使用条件。本仓库不附航拍视频或 Bing 图片文件。

## Excluded local trial assets / 未公开的本地试用素材

The public source snapshot omits `app/src/compact/assets/official-icons/` and `app/src/compact/assets/preview-posters/`, including their catalogs. These local trial directories contained two QQ Music/Kodi images and three film/show posters without a recorded redistribution grant. The previously tested local APK contains those assets and is therefore not distributed here. See README for the resulting optional icon-endpoint limitation.

公开源码省略上述两组目录及素材清单，其中包括 QQ 音乐/Kodi 的两张图片和三张影视海报；本地记录未提供公开再分发授权。此前已验的本地 APK 包含这些素材，因此本次不附带。相关可选图标接口限制见 README。

This is an independent customization and is not affiliated with or endorsed by Apple, Tencent, the named app brands, or the upstream author.

本项目是独立定制，与 Apple、腾讯、相关 App 品牌及上游作者不存在本次发布所宣称的隶属或背书关系。
