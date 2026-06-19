# 构建指南

## 安装依赖

### JDK
安装JDK17以上版本 [Oracle JDK](https://www.oracle.com/cn/java/technologies/downloads/)

### Android SDK (可选)
编译安卓目标时需要

推荐直接下载 [Android Studio](https://developer.android.com/studio) 进行配置

如果不希望安装Android Studio:
1. 在上下载页面底部下载对应平台的 "仅限命令行工具"
2. 解压后将 `cmdline-tools/bin` 目录加入 `PATH`
3. 接受许可协议 
   ```
   sdkmanager --licenses
   ```

### Xcode (可选)
编译iOS与macOS目标时需要

推荐直接从 App Store 下载并安装 [Xcode](https://apps.apple.com/cn/app/xcode/id497799835)

手动安装时建议使用26.3,不低于26版本

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

## 编译目标

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
zip -r iosApp-unsigned.ipa Payload
rm -rf Payload
```

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