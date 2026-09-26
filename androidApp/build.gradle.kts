import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import tv.hsrui.bolo.buildlogic.ResolvedVersionMetadata

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val appVersionMetadata = rootProject.extra["appVersionMetadata"] as ResolvedVersionMetadata

kotlin {
    compilerOptions {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
}

android {
    namespace = "tv.hsrui.bolo"
    ndkVersion = "28.2.13676358"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "tv.hsrui.bolo"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionMetadata.buildNumber
        versionName = appVersionMetadata.appDisplayVersion
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            merges -= "/META-INF/services/**"
            merges += "/META-INF/services/{coil3.util.FetcherServiceLoaderTarget,io.ktor.*,kotlinx.*}"
            excludes += "META-INF/services/coil3.util.DecoderServiceLoaderTarget"
        }
    }
    buildTypes {
        getByName("release") {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}



dependencies {
    implementation(projects.composeApp)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.uiTooling)
}

abstract class StagePlayerNative : Sync() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty
    init { into(outputDirectory) }
}
val stagePlayerJni = tasks.register<StagePlayerNative>("stagePlayerJni") {
    dependsOn(":nativePlayer:prepareAndroidNative")
    from(project(":nativePlayer").layout.buildDirectory.dir("android/jniLibs"))
    outputDirectory.set(layout.buildDirectory.dir("generated/nativePlayer/jniLibs"))
}
val stagePlayerLicenses = tasks.register<StagePlayerNative>("stagePlayerLicenses") {
    dependsOn(":nativePlayer:prepareAndroidNative")
    from(project(":nativePlayer").layout.buildDirectory.dir("android/licenses"))
    outputDirectory.set(layout.buildDirectory.dir("generated/nativePlayer/assets/licenses"))
}
androidComponents.onVariants { variant ->
    variant.sources.jniLibs?.addGeneratedSourceDirectory(stagePlayerJni, StagePlayerNative::outputDirectory)
    variant.sources.assets?.addGeneratedSourceDirectory(stagePlayerLicenses, StagePlayerNative::outputDirectory)
}
