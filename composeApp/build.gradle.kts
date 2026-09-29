import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.AbstractPodInstallTask
import org.jetbrains.kotlin.gradle.targets.native.tasks.PodBuildTask
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.INT
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import tv.hsrui.bolo.buildlogic.ResolvedVersionMetadata

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.buildkonfig)
    // CocoaPods 管理本地播放器 XCFramework 并生成 cinterop
    kotlin("native.cocoapods")
}

val appVersionMetadata = rootProject.extra["appVersionMetadata"] as ResolvedVersionMetadata
val iosDeploymentTarget = "16.0"
val isMacHost = System.getProperty("os.name").startsWith("Mac")
val (appVersionMajor, appVersionMinor) = appVersionMetadata.coreVersion.split('.')
val windowsPackageVersion =
    "$appVersionMajor.$appVersionMinor.${appVersionMetadata.buildNumber}"

kotlin {
    android {
        namespace = "tv.hsrui.bolo.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        androidResources {
            enable = true
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    cocoapods {
        version = appVersionMetadata.releaseVersion
        license = "GPL-3.0"
        summary = "Bolo Compose App"
        homepage = "https://hsrui.tv/bolo"
        ios.deploymentTarget = iosDeploymentTarget
        podfile = project.file("../iosApp/Podfile")

        pod("BoloNativePlayer") {
            version = "0.41.0"
            source = path(project.file("../nativePlayer/build/ios"))
        }

        framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm()

    applyDefaultHierarchyTemplate()

    sourceSets {
        val skiaMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(skiaMain)
        iosMain.get().dependsOn(skiaMain)
        val jvmAndAndroidMain by creating { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(jvmAndAndroidMain)
        jvmMain.get().dependsOn(jvmAndAndroidMain)
        val mobileMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.compose.webview.multiplatform)
            }
        }
        androidMain.get().dependsOn(mobileMain)
        iosMain.get().dependsOn(mobileMain)
        androidMain.dependencies {
            implementation(libs.media3.session)
            implementation(libs.coil.gif)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(projects.network)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.materialKolor)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.zoomimage.compose.coil3.core)
            implementation(libs.material.icons)
            implementation(libs.material3.adaptive)
            implementation(libs.qrose)
            implementation(libs.ksafe)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.jetbrains.material3.adaptiveNavigation3)
            implementation(libs.jetbrains.lifecycle.viewmodelNavigation3)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.dbus.java.core)
            runtimeOnly(libs.dbus.java.transport)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.jogl)
            implementation(libs.gluegen)
        }
    }
}

tasks.withType<PodBuildTask>().configureEach {
    // Pod 子任务只编译库，使用对应平台的通用目标，不继承 Xcode Run 的真机 UDID。
    targetDeviceIdentifier.unsetConvention()
    xcodeBuildSettings.put("IPHONEOS_DEPLOYMENT_TARGET", iosDeploymentTarget)
    xcodeBuildSettings.put("ARCHS", "arm64")
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "tv.hsrui.bolo.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb,
                TargetFormat.Rpm,
                TargetFormat.Exe
            )
            packageName = "Multi Bili"

            macOS {
                appCategory = "public.app-category.entertainment"
                packageVersion = appVersionMetadata.coreVersion
                packageBuildVersion = appVersionMetadata.buildNumber.toString()
                iconFile.set(project.file("src/jvmMain/icons/mac_icon.icns"))
            }
            linux {
                menuGroup = "AudioVideo;Video;Player;"
                packageVersion = appVersionMetadata.coreVersion
                appRelease = appVersionMetadata.buildNumber.toString()
                iconFile.set(project.file("src/jvmMain/icons/linux_icon.png"))
            }
            windows {
                packageVersion = windowsPackageVersion
                upgradeUuid = "CF9BD107-DCB2-5E72-9378-280860754B39"
                menu = true
                menuGroup = ""
                shortcut = true
                iconFile.set(project.file("src/jvmMain/icons/windows_icon.ico"))
            }
        }

    }
}

buildkonfig {
    packageName = "tv.hsrui.bolo"
    exposeObjectWithName = "BuildInfo"

    defaultConfigs {
        buildConfigField(STRING, "appVersionCore", appVersionMetadata.coreVersion)
        buildConfigField(STRING, "appReleaseChannel", appVersionMetadata.releaseChannel.name.lowercase())
        buildConfigField(INT, "appPrereleaseNumber", appVersionMetadata.prereleaseNumber.toString())
        buildConfigField(STRING, "appReleaseVersion", appVersionMetadata.releaseVersion)
        buildConfigField(STRING, "appDisplayVersion", appVersionMetadata.appDisplayVersion)
        buildConfigField(INT, "appBuildNumber", appVersionMetadata.buildNumber.toString())
        buildConfigField(STRING, "gitCommitSha", appVersionMetadata.commitSha)
        buildConfigField(STRING, "gitCommitSha7", appVersionMetadata.commitSha7)
        buildConfigField(STRING, "gitCommitSha12", appVersionMetadata.commitSha12)
        buildConfigField(BOOLEAN, "isGitDirty", appVersionMetadata.isDirty.toString())
    }
}

val desktopNativeResources = project(":nativePlayer").layout.buildDirectory.dir("desktop/resources")
kotlin.sourceSets.named("jvmMain") { resources.srcDir(desktopNativeResources) }
tasks.matching { it.name == "jvmProcessResources" }.configureEach {
    dependsOn(":nativePlayer:prepareDesktopNative")
}
if (isMacHost) {
    // Xcode 的 PATH 可能不含 Homebrew；普通及 synthetic Pod 安装共用自动查找结果。
    val cocoaPodsExecutable = providers.environmentVariable("PATH").orElse("").map { path ->
        (path.split(File.pathSeparator) + listOf("/opt/homebrew/bin", "/usr/local/bin"))
            .filter(String::isNotBlank)
            .map { File(it, "pod") }
            .firstOrNull { it.isFile && it.canExecute() }
    }
    tasks.withType<AbstractPodInstallTask>().configureEach {
        if (!podExecutablePath.isPresent) {
            podExecutablePath.set(layout.file(cocoaPodsExecutable))
        }
    }
}
tasks.matching { it.name == "podspec" || it.name.startsWith("podGen") || it.name == "podInstall" || it.name.startsWith("podInstallSynthetic") || it.name == "generateDefBoloNativePlayer" || it.name.startsWith("cinteropBoloNativePlayer") }.configureEach {
    if (isMacHost) {
        dependsOn(":nativePlayer:prepareIosNative")
    } else {
        // 非 macOS 无法生成本地 Pod；同时跳过任务，避免校验尚未生成的输入目录。
        enabled = false
    }
}
