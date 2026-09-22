<div align="center">
<img src=Icon.svg alt="Multi Bili" width="256">

# Multi Bili

一个基于[KMP (Kotlin MultiPlatform)](https://kotlinlang.org/multiplatform/)的开源多平台B站客户端实现

</div>

### 不包含以下特性：

- :thumbsup: 丝滑的使用体验
- :pencil2: 精心设计的UI
- :art: 高水平审美
- :zap: 运行流畅
- :100: 无BUG

> [!NOTE]
> 由于项目目前处于早期开发阶段,可能频繁发生架构变动,暂不接受PR,有建议欢迎提交Issues
> 
> 本项目目标不是实现官方客户端的所有功能,而是优先专注于视频/番剧,当前阶段不会添加关于 私信/动态/直播 的内容

> [!CAUTION]
> 为了项目的存续,**不要在国内社交平台公开推广**

### 鸣谢
- [SocialSisterYi/bilibili-API-collect](https://github.com/SocialSisterYi/bilibili-API-collect): 提供B站Api信息
- [jordond/MaterialKolor](https://github.com/jordond/MaterialKolor): 用于创建动态Material3色彩
- [KevinnZou/compose-webview-multiplatform](https://github.com/KevinnZou/compose-webview-multiplatform): 用于加载ios与安卓的网页登录页面 
- [ioannisa/KSafe](https://github.com/ioannisa/KSafe): 加密存储登录Cookie等敏感信息
- [FFmpeg](https://ffmpeg.org/) / [libmpv](https://github.com/mpv-player/mpv): 底层播放器平台实现
- [panpf/zoomimage](https://github.com/panpf/zoomimage): 处理评论图片查看器的缩放与手势交互，以及通过子采样优化大图显示

### 开源协议
- 本项目自有代码使用 [GPL-3.0 license](LICENSE) 开源
- iOS、macOS、Windows 播放器组件遵循 [LGPL-2.1-or-later](COPYING.LGPLv2.1)
- Android、Linux 播放器组件遵循 [LGPL-3.0-or-later](COPYING.LGPLv3)
