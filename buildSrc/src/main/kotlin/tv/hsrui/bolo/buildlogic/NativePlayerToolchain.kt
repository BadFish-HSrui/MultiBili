package tv.hsrui.bolo.buildlogic

import org.gradle.api.GradleException
import java.io.File
import java.util.Properties

internal class NativePlayerToolchain(val target: String, val host: NativePlayerEnvironment) {
    val system = target.substringBefore('-')
    val arch = target.substringAfter('-')
    val machine = linkedMapOf(
        "system" to system,
        "cpu_family" to when (arch) { "arm64" -> "aarch64"; "armv7" -> "arm"; "x64" -> "x86_64"; "x86" -> "x86"; else -> error("Unknown architecture: $arch") },
        "cpu" to when (arch) { "arm64" -> "aarch64"; "armv7" -> "arm"; "x64" -> "x86_64"; else -> arch },
        "endian" to "little",
    )
    val flags = mutableListOf("-fPIC")
    val link = mutableListOf<String>()
    val environment = host.environment.toMutableMap()
    val identity = linkedMapOf<String, Any?>("target" to target, "tools" to host.identity)
    var cc: File
    var cpp: File
    var ar: File
    var strip: File
    var ranlib: File
    val apple = system in listOf("ios", "iossim", "macos")

    init {
        if (apple) {
            if (!host.mac) throw GradleException("$target requires macOS and Xcode")
            val sdk = when (system) { "ios" -> "iphoneos"; "iossim" -> "iphonesimulator"; else -> "macosx" }
            val sdkPath = host.run("xcrun", "--sdk", sdk, "--show-sdk-path", capture = true).trim()
            val sdkBuild = host.run("xcrun", "--sdk", sdk, "--show-sdk-build-version", capture = true).trim()
            fun xcodeTool(name: String) = File(host.run("xcrun", "--find", name, capture = true).trim())
            cc = xcodeTool("clang"); cpp = xcodeTool("clang++")
            ar = xcodeTool("ar"); strip = xcodeTool("strip"); ranlib = xcodeTool("ranlib")
            val minimum = if (system == "macos") "12.0" else "16.0"
            val triple = (if (arch == "arm64") "arm64" else "x86_64") + "-apple-" +
                if (system == "macos") "macos$minimum" else "ios$minimum" + if (system == "iossim") "-simulator" else ""
            flags += listOf("-target", triple, "-isysroot", sdkPath)
            link += listOf("-target", triple, "-isysroot", sdkPath)
            machine["system"] = "darwin"
            identity.putAll(mapOf("sdkPath" to sdkPath, "sdkBuild" to sdkBuild, "minimum" to minimum))
        } else if (system == "android") {
            val local = Properties()
            host.root.parentFile.resolve("local.properties").takeIf(File::isFile)?.inputStream()?.use(local::load)
            val sdk = environment["ANDROID_HOME"] ?: environment["ANDROID_SDK_ROOT"] ?: local.getProperty("sdk.dir")
            val ndk = environment["ANDROID_NDK_HOME"]?.let(::File) ?: File(sdk.orEmpty(), "ndk/$NDK_VERSION")
            if (!ndk.resolve("source.properties").isFile) throw GradleException(
                "Install Android NDK $NDK_VERSION with sdkmanager \"ndk;$NDK_VERSION\"; see docs/build-guide.md.",
            )
            val prebuilt = ndk.resolve("toolchains/llvm/prebuilt").listFiles().orEmpty()
                .firstOrNull { it.isDirectory && it.name.startsWith(if (host.mac) "darwin-" else if (host.windows) "windows-" else "linux-") }
                ?: throw GradleException("No NDK toolchain for this host in $ndk")
            val bin = prebuilt.resolve("bin")
            val suffix = if (host.windows) ".exe" else ""
            cc = bin.resolve("clang$suffix"); cpp = bin.resolve("clang++$suffix")
            ar = bin.resolve("llvm-ar$suffix"); strip = bin.resolve("llvm-strip$suffix"); ranlib = bin.resolve("llvm-ranlib$suffix")
            val triple = when (arch) {
                "arm64" -> "aarch64-linux-android"; "armv7" -> "armv7a-linux-androideabi"
                "x64" -> "x86_64-linux-android"; else -> "i686-linux-android"
            } + "24"
            flags += "--target=$triple"
            link += listOf("--target=$triple", "-static-libstdc++", "-Wl,-z,max-page-size=16384", "-Wl,-z,common-page-size=16384")
            identity["ndk"] = ndk.resolve("source.properties").readText()
            identity["minimum"] = 24
        } else {
            if (system !in listOf("windows", "linux")) throw GradleException("Unsupported native target: $target")
            if (system == "windows" && !host.windows) throw GradleException("Build Windows on Windows using MSYS2 UCRT64")
            if (system == "linux" && (host.windows || host.mac)) throw GradleException("Build Linux on Linux")
            cc = host.tool(if (system == "windows") "gcc" else "cc")
            cpp = host.tool(if (system == "windows") "g++" else "c++")
            ar = host.tool("ar"); strip = host.tool("strip"); ranlib = host.tool("ranlib")
            if (system == "windows") link += listOf("-static", "-static-libgcc", "-static-libstdc++")
        }
        // 使用同一套归档工具；FFmpeg configure 会读取小写 ranlib 环境变量。
        environment["ranlib"] = nativePath(ranlib)
        environment["RANLIB"] = nativePath(ranlib)
        identity["compiler"] = host.run(cc, "--version", capture = true).trim()
        identity["binaries"] = listOf(cc, cpp, ar, strip, ranlib).distinct().associate {
            nativePath(it.canonicalFile) to nativeSha(it)
        }
        identity["flags"] = flags.toList()
        identity["linkFlags"] = link.toList()
        identity["environment"] = listOf("JAVA_HOME", "CFLAGS", "CXXFLAGS", "LDFLAGS", "CPATH", "LIBRARY_PATH", "SOURCE_DATE_EPOCH")
            .associateWith { environment[it].orEmpty() }
        if (system == "linux") identity["systemDependencies"] =
            host.run("pkg-config", "--modversion", "libpulse", "alsa", "libva", "libva-drm", capture = true).trim()
    }

    fun crossFile(file: File, compileFlags: List<String>) {
        val binaries = mapOf("c" to cc, "cpp" to cpp, "objc" to cc, "objcpp" to cpp, "ar" to ar, "strip" to strip,
            "pkg-config" to host.tool("pkg-config"), "python" to host.tool("python3"), "python3" to host.tool("python3"))
        fun quoted(value: String) = "'" + value.replace("\\", "/").replace("'", "\\'") + "'"
        fun array(values: List<String>) = values.joinToString(", ", "[", "]", transform = ::quoted)
        file.writeText(buildString {
            appendLine("[binaries]")
            binaries.forEach { (name, path) -> appendLine("$name = ${quoted(nativePath(path))}") }
            appendLine("[host_machine]")
            machine.forEach { (name, value) -> appendLine("$name = ${quoted(value)}") }
            appendLine("[properties]\nneeds_exe_wrapper = true\n[built-in options]")
            for (language in listOf("c", "cpp", "objc", "objcpp")) {
                appendLine("${language}_args = ${array(compileFlags)}")
                appendLine("${language}_link_args = ${array(compileFlags + link)}")
            }
        })
        file.resolveSibling("native.ini").writeText("[binaries]\npython = ${quoted(nativePath(host.tool("python3")))}\n" +
            "python3 = ${quoted(nativePath(host.tool("python3")))}\n")
    }

    companion object {
        const val NDK_VERSION = "28.2.13676358"
    }
}

private fun File.resolveSibling(name: String): File = parentFile.resolve(name)
