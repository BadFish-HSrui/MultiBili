# PCDN 分类数据

## 数据来源

[PCDN-AnalysisData.json](PCDN-AnalysisData.json) (2026-09-21) (33视频记录、893 URL、151主机名)

[PCDN-AnalysisData-1.json](PCDN-AnalysisData-1.json) (2026-09-22) (100视频记录、4302 URL、342主机名)

## 分类表

| 类型        | 厂商                 | 域名                                                                                                                                           | 端口      | 路径                 | 主机数 | 主机占比  | URL 数 | URL 占比 |
|-----------|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------|---------|--------------------|-----|-------|-------|--------|
| 官方CDN     | B站自建               | `cn-jstz-cu-01-0{2,3,4}.bilivideo.com`、`cn-zjhz-cu-01-01.bilivideo.com`                                                                      | `:443`  | `/upgcxcode/….m4s` | 4   | 1.1%  | 259   | 5.0%   |
| 服务商 CDN   | 阿里云                | `upos-sz-mirrorali{,b}.bilivideo.com`、`upos-sz-estgoss.bilivideo.com`                                                                        | `:443`  | `/upgcxcode/….m4s` | 3   | 0.8%  | 592   | 11.4%  |
| 服务商 CDN   | 百度云                | `upos-sz-mirrorbd{,b}.bilivideo.com`                                                                                                         | `:443`  | `/upgcxcode/….m4s` | 2   | 0.5%  | 225   | 4.3%   |
| 服务商 CDN   | 华为云                | `upos-sz-mirrorhw{,b,o1}.bilivideo.com`、`upos-sz-mirror08{c,h}.bilivideo.com`、`upos-sz-estghw.bilivideo.com`                                 | `:443`  | `/upgcxcode/….m4s` | 6   | 1.6%  | 933   | 18.0%  |
| 服务商 CDN   | 腾讯云                | `upos-sz-mirrorcos{,b,o1}.bilivideo.com`、`upos-sz-estgcos.bilivideo.com`、`upos-sz-mirror14b.bilivideo.com`、`upos-sz-mirrorzos.bilivideo.com` | `:443`  | `/upgcxcode/….m4s` | 6   | 1.6%  | 1933  | 37.2%  |
| PCDN/MCDN | B站自建PCDN           | `xy<IP编码>xy.mcdn.bilivideo.cn`                                                                                                               | `:8082` | `/v1/resource/…`   | 325 | 86.0% | 770   | 14.8%  |
| PCDN/MCDN | mountaintoys（不同凡响） | `*.edge.mountaintoys.cn`                                                                                                                     | `:4483` | `/upgcxcode/….m4s` | 32  | 8.5%  | 483   | 9.3%   |

合计 378 主机 / 5195 URL。

## 按类型汇总

| 类型             | 主机数 | 主机占比  | URL 数 | URL 占比 |
|----------------|-----|-------|-------|--------|
| 官方CDN          | 4   | 1.1%  | 259   | 5.0%   |
| 服务商 CDN        | 17  | 4.5%  | 3683  | 70.9%  |
| PCDN/MCDN      | 357 | 94.4% | 1253  | 24.1%  |
| ├ MCDN         | 325 | 86.0% | 770   | 14.8%  |
| └ mountaintoys | 32  | 8.5%  | 483   | 9.3%   |

## 端口与路径分布

| 端口      | URL 数 | 使用方          |
|---------|-------|--------------|
| `:443`  | 3942  | 官方/服务商 CDN   |
| `:8082` | 770   | MCDN         |
| `:4483` | 483   | mountaintoys |

| 路径                 | URL 数 | 使用方                     |
|--------------------|-------|-------------------------|
| `/upgcxcode/….m4s` | 4425  | 官方/服务商 CDN、mountaintoys |
| `/v1/resource/…`   | 770   | 官方MCDN                  |

## 音视频与默认/备用分布

| 部分 | 位置 | URL 数 | 服务商 CDN | PCDN/MCDN | 官方CDN |
|----|----|-------|---------|-----------|-------|
| 视频 | 默认 | 1396  | 638     | 621       | 137   |
| 视频 | 备用 | 2659  | 2321    | 265       | 73    |
| 音频 | 默认 | 397   | 125     | 259       | 13    |
| 音频 | 备用 | 743   | 599     | 108       | 36    |

PCDN/MCDN 候选出现在 120/133 条视频记录中；默认候选地址占比为 880/1793（49.1%）。
