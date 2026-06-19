<div align="center">
<img src=Icon.svg alt="Multi Bili" width="256">

# Multi Bili

一个基于[KMP (Kotlin MultiPlatform)](https://kotlinlang.org/multiplatform/)的开源多平台B站客户端实现

</div>

### :warning:: 本项目目的不是实现官方客户端的所有功能,而是更专注于视频

> 启动这个项目的原因就是我不喜欢官方客户端越来越多的在主页与视频下方推荐广告、会员购、专栏、直播、PGC内容

### 不包含以下特性：

- :thumbsup: 丝滑的使用体验
- :pencil2: 精心设计的UI
- :art: 高水平审美
- :zap: 运行流畅
- :100: 无BUG

### 调试与构建
需要使用安装了[Kotlin Multiplatform 插件](https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform)的[Android Studio](https://developer.android.com/studio)加载本项目进行调试与构建
> 也可以使用安装了此插件的[IDEA](https://www.jetbrains.com/idea/),但IDEA的AGP版本支持相较于Android Studio滞后

通过命令行构建参考[构建指南](docs/build-guide.md)

### 鸣谢
- [SocialSisterYi/bilibili-API-collect](https://github.com/SocialSisterYi/bilibili-API-collect): 提供B站Api信息
- [jordond/MaterialKolor](https://github.com/jordond/MaterialKolor): 用于创建动态Material3色彩
- [KevinnZou/compose-webview-multiplatform](https://github.com/KevinnZou/compose-webview-multiplatform): 用于加载ios与安卓的网页登录页面 
- [ioannisa/KSafe](https://github.com/ioannisa/KSafe): 加密存储登录Cookie等敏感信息
- [vlcKit](https://code.videolan.org/videolan/VLCKit) / [libVlc](https://code.videolan.org/videolan/vlc) / [vlcj](https://github.com/caprica/vlcj): 底层播放器平台实现

### 开源协议
- 本项目代码使用 [GPL-3.0 license](LICENSE) 开源
- 由于动态链接了 [vlcKit](https://code.videolan.org/videolan/VLCKit), iOS构建产物受 [LGPL-2.1](LICENSE-LGPL-2.1) 约束
- 由于动态链接了 [libVlc](https://code.videolan.org/videolan/vlc), 安卓构建产物受 [LGPL-2.1](LICENSE-LGPL-2.1) 约束
- 由于使用了 [vlcj](https://github.com/caprica/vlcj), 桌面端(jvm)构建产物受 [GPL-3.0](LICENSE-GPL-3.0) 约束