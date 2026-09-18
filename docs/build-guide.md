# 构建指南

## 安装依赖

调试推荐使用安装了[Kotlin Multiplatform 插件](https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform)的[Android Studio](https://developer.android.com/studio)加载本项目；构建可完全使用命令行。

### JDK
安装 JDK 21 [Oracle JDK](https://www.oracle.com/cn/java/technologies/downloads/)

### 原生播放器构建工具

- Git、curl、Bash、GNU Make
- Python 3.10+、Jinja2、MarkupSafe
- Meson 1.3.0+、Ninja 1.11.1+、pkg-config 0.29+（或 pkgconf）
- CMake 3.16+（Android/Linux）
- NASM 2.16+（x86/x86_64）
- C/C++ 工具链：Xcode、Android NDK、Linux GCC/Clang 或 MSYS2 UCRT64 GCC

**macOS**
```bash
xcode-select --install
brew install meson ninja cmake pkgconf nasm jinja2-cli
```

**Ubuntu / Debian**
```bash
sudo apt-get update
sudo apt-get install -y build-essential git curl bash pkg-config nasm \
  python3 python3-jinja2 python3-markupsafe meson ninja-build cmake fakeroot rpm \
  libpulse-dev libasound2-dev libva-dev libgl-dev libdrm-dev libdisplay-info-dev
```

**Windows（MSYS2 UCRT64）**
```bash
pacman -S --needed make diffutils git curl \
  mingw-w64-ucrt-x86_64-gcc mingw-w64-ucrt-x86_64-pkgconf mingw-w64-ucrt-x86_64-nasm \
  mingw-w64-ucrt-x86_64-meson mingw-w64-ucrt-x86_64-ninja mingw-w64-ucrt-x86_64-cmake \
  mingw-w64-ucrt-x86_64-python mingw-w64-ucrt-x86_64-python-jinja mingw-w64-ucrt-x86_64-python-markupsafe
```

构建前将 `C:\msys64\ucrt64\bin`、`C:\msys64\usr\bin` 依次加入 PATH。

### Android SDK (可选)
编译安卓目标时需要

安装 NDK `28.2.13676358`：

```bash
sdkmanager "ndk;28.2.13676358"
```

推荐直接下载 [Android Studio](https://developer.android.com/studio) 进行配置

如果不希望安装Android Studio:
1. 在上方下载页面底部下载对应平台的 "仅限命令行工具"
2. 解压后将 `cmdline-tools` 目录重命名为 `latest`，放入 `<SDK>/cmdline-tools/latest`
3. 将 `<SDK>/cmdline-tools/latest/bin` 加入 `PATH`
4. 接受许可协议
   ```
   sdkmanager --licenses
   ```

SDK 位置需通过 `ANDROID_HOME`、`ANDROID_SDK_ROOT` 或 `local.properties` 的 `sdk.dir` 提供；缺失的 SDK Platform 与 Build-Tools 在许可接受后由 Gradle 自动下载。

### Xcode (可选)
编译iOS与macOS目标时需要

推荐直接从 App Store 下载并安装 [Xcode](https://apps.apple.com/cn/app/xcode/id497799835)

手动安装时版本要求不低于26

### CocoaPods (可选)
编译iOS目标时需要

**Homebrew**
```bash
brew install cocoapods
```
**RubyGems**
```bash
sudo gem install cocoapods
```

## 构建前置条件

- 需要完整 Git 历史；不要使用浅克隆或源码压缩包。
- 首次构建需要联网。
- 每个平台的产物只能在该平台上构建，其他平台的打包任务会被跳过。
- Android 目标同时需要 Android SDK/NDK 与原生播放器构建工具。

## 编译目标

初始化源码：
```bash
git submodule update --init --recursive
```

### iOS
> **仅限macOS,需安装[Xcode](#xcode-可选)和[CocoaPods](#cocoapods-可选)**
```bash
./gradlew :composeApp:podInstall
cd iosApp
xcodebuild build \
  -workspace iosApp.xcworkspace \
  -scheme iosApp \
  -configuration Release \
  -sdk iphoneos \
  -derivedDataPath build \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY="" \
  AD_HOC_CODE_SIGNING_ALLOWED=YES
mkdir -p Payload
cp -r build/Build/Products/Release-iphoneos/*.app Payload/
ditto -c -k --sequesterRsrc --keepParent Payload iosApp-unsigned.ipa
rm -rf Payload
```

产物：`iosApp/iosApp-unsigned.ipa`

### Android
> **需安装[Android SDK](#android-sdk-可选)**

**Windows**
```cmd
gradlew.bat :androidApp:assembleRelease
```

**macOS / Linux**
```bash
./gradlew :androidApp:assembleRelease
```

产物：`androidApp/build/outputs/apk/release/`

### 桌面端

**macOS**
> **需安装[Xcode](#xcode-可选)**

- DMG：
  ```bash
  ./gradlew :composeApp:packageDmg
  ```

**Linux**
- DEB：
  ```bash
  ./gradlew :composeApp:packageDeb
  ```
- RPM:
  ```bash
  ./gradlew :composeApp:packageRpm
  ```

**Windows**
- MSI：
  ```cmd
  gradlew.bat :composeApp:packageMsi
  ```
- EXE：
  ```cmd
  gradlew.bat :composeApp:packageExe
  ```

首次打包 MSI/EXE 会联网下载 WiX 3.11；已有 WiX 时用 `WIX_PATH` 指定。

产物位于 `composeApp/build/compose/binaries/main/` 下对应格式的子目录。

## 构建缓存

原生构建缓存位于 `~/.gradle/bolo-native`；普通 clean 不会清理，需要时运行：

```bash
./gradlew :nativePlayer:cleanNativeCache
```
