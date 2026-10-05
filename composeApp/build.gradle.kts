import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
}

val appVersionMetadata = rootProject.extra["appVersionMetadata"] as ResolvedVersionMetadata
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

    val nativePlayer = project(":nativePlayer")
    val nativeHeaders = nativePlayer.layout.projectDirectory.dir("src")
    val nativeFrameworks = listOf(
        "UIKit", "Foundation", "AVFoundation", "AudioToolbox", "CoreAudio", "CoreGraphics",
        "CoreMedia", "CoreVideo", "VideoToolbox", "OpenGLES", "QuartzCore", "Security", "CoreFoundation",
    )
    listOf(
        Triple(iosArm64(), "ios-arm64", "prepareIosArm64Native"),
        Triple(iosSimulatorArm64(), "iossim-arm64", "prepareIosSimulatorArm64Native"),
    ).forEach { (iosTarget, nativeTarget, prepareTask) ->
        val nativeFrameworkDirectory = nativePlayer.layout.buildDirectory.dir(nativeTarget)
        val interop = iosTarget.compilations.getByName("main").cinterops.create("BoloNativePlayer") {
            definitionFile.set(project.file("src/nativeInterop/cinterop/BoloNativePlayer.def"))
            includeDirs(nativeHeaders)
        }
        tasks.named(interop.interopProcessingTaskName).configure {
            inputs.files(nativeHeaders.file("bolo_mpv.h"), nativeHeaders.file("bolo_download.h"), nativeHeaders.file("BoloMpvView.h"))
            enabled = isMacHost
        }
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
        iosTarget.binaries.configureEach {
            linkerOpts("-F${nativeFrameworkDirectory.get().asFile.absolutePath}", "-framework", "BoloNativePlayer", "-ObjC")
            linkerOpts(nativeFrameworks.flatMap { listOf("-framework", it) })
            linkerOpts("-lc++", "-liconv", "-lz", "-lbz2")
            linkTaskProvider.configure {
                if (isMacHost) dependsOn(":nativePlayer:$prepareTask")
                inputs.dir(nativeFrameworkDirectory)
            }
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
            implementation(libs.filekit.dialogs)
            implementation(libs.dbus.java.core)
            runtimeOnly(libs.dbus.java.transport)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.jogl)
            implementation(libs.gluegen)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "tv.hsrui.bolo.MainKt"
        buildTypes.release.proguard.configurationFiles.from(project.file("desktop.pro"))

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
                modules("jdk.security.auth")
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
