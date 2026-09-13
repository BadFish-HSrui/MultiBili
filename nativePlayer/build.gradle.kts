import tv.hsrui.bolo.buildlogic.NativePlayerBuildTask

plugins { base }

fun nativeTask(name: String, nativeCommand: String) = tasks.register<NativePlayerBuildTask>(name) {
    group = "native player"
    sourceDirectory.set(layout.projectDirectory)
    nativeCacheDirectory.set(gradle.gradleUserHomeDir.resolve("bolo-native"))
    command.set(nativeCommand)
    parallelJobs.set(providers.environmentVariable("BOLO_NATIVE_JOBS").map(String::toInt)
        .orElse(Runtime.getRuntime().availableProcessors().coerceAtMost(12)))
}
nativeTask("prepareAndroidNative", "android")
nativeTask("prepareIosNative", "ios")
nativeTask("prepareDesktopNative", "desktop")
nativeTask("checkNativeSources", "check-sources")
nativeTask("cleanNativeCache", "clean-cache")
