import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.PodBuildTask
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.INT
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import tv.hsrui.bolo.buildlogic.ResolvedVersionMetadata

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.buildkonfig)
    // CocoaPods 插件 — 自动管理 iOS 依赖（VLCKit）并生成 cinterop
    kotlin("native.cocoapods")
}

val appVersionMetadata = rootProject.extra["appVersionMetadata"] as ResolvedVersionMetadata
val appBuildOrigin = rootProject.extra["appBuildOrigin"] as String
val appOfficialBuild = rootProject.extra["appOfficialBuild"] as Boolean
val iosDeploymentTarget = "16.0"
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
        summary = "Bolo Compose App"
        homepage = "https://hsrui.tv/bolo"
        ios.deploymentTarget = iosDeploymentTarget
        podfile = project.file("../iosApp/Podfile")

        pod("MobileVLCKit") {
            version = "~> 3.7"
        }

        framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.libvlc)
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
            implementation(libs.materialKolor)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.material.icons)
            implementation(libs.material3.adaptive)
            implementation(libs.compose.webview.multiplatform)
            implementation(libs.ksafe)
            implementation(libs.ksafe.compose)
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
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.vlcj)
        }
    }
}

tasks.withType<PodBuildTask>().configureEach {
    xcodeBuildSettings.put("IPHONEOS_DEPLOYMENT_TARGET", iosDeploymentTarget)
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
                packageVersion = appVersionMetadata.coreVersion
                packageBuildVersion = appVersionMetadata.buildNumber.toString()
                iconFile.set(project.file("src/jvmMain/icons/mac_icon.icns"))
            }
            linux {
                packageVersion = appVersionMetadata.coreVersion
                appRelease = appVersionMetadata.buildNumber.toString()
                iconFile.set(project.file("src/jvmMain/icons/linux_icon.png"))
            }
            windows {
                packageVersion = windowsPackageVersion
                upgradeUuid = "CF9BD107-DCB2-5E72-9378-280860754B39"
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
        buildConfigField(STRING, "buildOrigin", appBuildOrigin)
        buildConfigField(BOOLEAN, "isOfficialBuild", appOfficialBuild.toString())
        buildConfigField(STRING, "artifactVersion", appVersionMetadata.artifactVersion)
    }
}
