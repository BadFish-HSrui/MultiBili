import tv.hsrui.bolo.buildlogic.NativePlayerBuildTask

plugins { base }

fun nativeTask(name: String, nativeCommand: String) = tasks.register<NativePlayerBuildTask>(name) {
    group = "native player"
    sourceDirectory.set(layout.projectDirectory)
    nativeBuildDirectory.set(layout.buildDirectory)
    mustRunAfter(tasks.named("clean"))
    command.set(nativeCommand)
    parallelJobs.set(providers.environmentVariable("BOLO_NATIVE_JOBS").map(String::toInt)
        .orElse(Runtime.getRuntime().availableProcessors().coerceAtMost(12)))
}
nativeTask("prepareAndroidNative", "android")
listOf(
    "prepareIosArm64Native" to "ios-arm64",
    "prepareIosSimulatorArm64Native" to "iossim-arm64",
).forEach { (name, target) ->
    nativeTask(name, target).configure {
        val isMacHost = System.getProperty("os.name").startsWith("Mac")
        onlyIf("iOS native libraries require macOS and Xcode") { isMacHost }
    }
}
nativeTask("prepareDesktopNative", "desktop")
nativeTask("checkNativeSources", "check-sources")
