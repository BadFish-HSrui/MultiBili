package tv.hsrui.bolo.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.security.MessageDigest

internal class NativePlayerBuild(
    val root: File,
    private val buildDirectory: File,
    private val logger: Logger,
    private val jobs: Int,
) {
    private val cache = buildDirectory.resolve("cache")
    private val host = NativePlayerEnvironment(root, buildDirectory, logger)
    private val dependencies by lazy { nativeReadJson(root.resolve("dependencies.lock.json")) }
    private val recipes by lazy {
        val code = root.parentFile.resolve("buildSrc/src/main/kotlin/tv/hsrui/bolo/buildlogic")
        code.listFiles().orEmpty().filter { it.name.startsWith("NativePlayer") && it.extension == "kt" }
            .associate { it.name to nativeSha(it) }
    }

    fun execute(command: String) {
        require(jobs > 0) { "BOLO_NATIVE_JOBS must be positive" }
        if (command == "check-sources") { logger.lifecycle(nativeJson(sourceIdentity())); return }
        val targets = when (command) {
            "android" -> listOf("android-arm64", "android-armv7", "android-x86", "android-x64")
            "ios" -> listOf("ios-arm64", "iossim-arm64")
            "desktop" -> listOf((if (host.mac) "macos" else if (host.windows) "windows" else "linux") + "-" +
                when (System.getProperty("os.arch")) { "aarch64", "arm64" -> "arm64"; "amd64", "x86_64" -> "x64"; else -> error("Unsupported desktop architecture") })
            else -> throw GradleException("Unknown native command: $command")
        }
        host.prepare(targets)
        nativeLock(cache.resolve("locks/prepare-$command")) {
            val bundles = targets.associateWith(::build)
            prepare(command, bundles)
        }
    }

    private fun trackedFiles(source: File): List<String> = host.run("git", "-C", source,
        "ls-files", "--cached", "--others", "--exclude-standard", "-z", capture = true)
        .split('\u0000').filter(String::isNotEmpty).distinct().sorted()

    private fun sourceIdentity(): Map<String, Any?> = listOf("ffmpeg", "mpv").associateWith { name ->
        val source = root.resolve("vendor/$name")
        if (!source.resolve(".git").exists()) throw GradleException("Missing submodule $name: run git submodule update --init --recursive")
        val digest = MessageDigest.getInstance("SHA-256")
        trackedFiles(source).forEach { relative ->
            val file = source.resolve(relative)
            digest.update((relative + '\u0000').toByteArray())
            when {
                Files.isSymbolicLink(file.toPath()) -> digest.update(Files.readSymbolicLink(file.toPath()).toString().toByteArray())
                file.isFile -> file.inputStream().use { input ->
                    val content = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(128 * 1024)
                    while (true) { val count = input.read(buffer); if (count < 0) break; content.update(buffer, 0, count) }
                    digest.update(content.digest())
                }
                else -> digest.update("deleted".toByteArray())
            }
        }
        mapOf("commit" to host.run("git", "-C", source, "rev-parse", "HEAD", capture = true).trim(),
            "content" to digest.digest().joinToString("") { "%02x".format(it) })
    }

    fun sourceCopy(name: String, destination: File) {
        val source = root.resolve("vendor/$name")
        destination.mkdirs()
        trackedFiles(source).forEach { relative ->
            val file = source.resolve(relative)
            if (Files.exists(file.toPath(), NOFOLLOW_LINKS)) nativeCopyFile(file, destination.resolve(relative))
        }
    }

    fun dependencySource(name: String, destination: File) {
        val item = dependencies[name] as? Map<*, *> ?: error("Unknown dependency: $name")
        val sha = item["sha256"] as String
        val archive = cache.resolve("downloads/$sha.tar.gz")
        nativeLock(cache.resolve("locks/download-$name")) {
            if (!archive.isFile || nativeSha(archive) != sha) {
                archive.parentFile.mkdirs()
                val local = buildDirectory.resolve("downloads/$name.tar.gz")
                val partial = archive.resolveSibling("$sha.partial")
                try {
                    if (local.isFile && nativeSha(local) == sha) nativeCopyFile(local, partial)
                    else host.run("curl", "--fail", "--location", "--retry", "3", "--connect-timeout", "20", "--max-time", "600",
                        item["url"] as String, "--output", partial)
                    if (nativeSha(partial) != sha) throw GradleException("Source checksum mismatch: $name")
                    Files.move(partial.toPath(), archive.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                } finally { nativeDelete(partial) }
            }
        }
        val unpack = destination.resolveSibling("$name-unpack")
        try {
            nativeUnpack(archive, unpack)
            // 解压期间选择解压目录的 Finder 窗口会写入 .DS_Store；归档内容本身仍然完整，只需清掉这些元数据。
            nativeFiles(unpack).filter { nativeMetadataEntry(it.name) }.forEach(::nativeDelete)
            val entries = unpack.listFiles().orEmpty()
            if (entries.size != 1 || !entries[0].isDirectory) throw GradleException("Invalid source archive: $name")
            Files.move(entries[0].toPath(), destination.toPath())
        } finally {
            // 校验失败也必须清理解压目录，否则残留的目录会让每次重试都停在同一处。
            nativeDelete(unpack)
        }
    }

    private fun build(target: String): File {
        val chain = NativePlayerToolchain(target, host)
        val source = sourceIdentity()
        val bridge = nativeFiles(root.resolve("src")).filter(File::isFile)
            .associate { it.relativeTo(root).invariantSeparatorsPath to nativeSha(it) }
        val identity = mapOf("schema" to 2, "sources" to source, "dependencies" to dependencies, "toolchain" to chain.identity,
            "recipe" to recipes + bridge)
        val fingerprint = nativeHash(nativeJson(identity))
        val bundle = cache.resolve("bundles/$target/$fingerprint")
        return nativeLock(cache.resolve("locks/$target-$fingerprint")) {
            if (nativeValidBundle(bundle, fingerprint)) {
                logger.lifecycle("Native cache hit: {} {}", target, fingerprint)
                return@nativeLock bundle
            }
            val work = buildDirectory.resolve("work/$target/kotlin-$fingerprint")
            nativeDelete(work)
            work.mkdirs()
            nativeCopyTree(root.resolve("src"), work.resolve("bridge"))
            nativeFiles(work.resolve("bridge")).filter(File::isFile).forEach { file ->
                if (nativeSha(file) != bridge["src/" + file.relativeTo(work.resolve("bridge")).invariantSeparatorsPath]) {
                    throw GradleException("Bridge changed while preparing native build; retry")
                }
            }
            val libraries = NativePlayerLibraries(this, chain, work, jobs)
            val libraryRecipe = recipes.filterKeys { it in listOf("NativePlayerLibraries.kt", "NativePlayerBuildSupport.kt", "NativePlayerToolchain.kt") }
            // 编排源码也参与库指纹，防止源码复制、解压或依赖选择变化后错误复用。
            val libraryIdentity = identity + ("recipe" to (libraryRecipe + ("orchestration" to recipes.getValue("NativePlayerBuild.kt"))))
            val libraryKey = nativeHash(nativeJson(libraryIdentity))
            val libraryCache = cache.resolve("libraries/$target/$libraryKey")
            nativeLock(cache.resolve("locks/libraries-$target-$libraryKey")) {
                if (nativeValidBundle(libraryCache, libraryKey)) {
                    logger.lifecycle("Native library cache hit: {} {}", target, libraryKey)
                    nativeCopyTree(libraryCache.resolve("prefix"), libraries.prefix)
                    val oldPrefix = libraryCache.resolve("prefix-path.txt").readText()
                    nativeFiles(libraries.prefix).filter { it.extension == "pc" }.forEach {
                        it.writeText(it.readText().replace(oldPrefix, nativePath(libraries.prefix)))
                    }
                    nativeCopyTree(libraryCache.resolve("licenses"), work.resolve("licenses"))
                } else {
                    logger.lifecycle("Building native libraries: {}", target)
                    libraries.compile()
                    val licenses = work.resolve("licenses").apply { mkdirs() }
                    nativeFiles(libraries.sources).filter { it.isFile && it.name.lowercase().let { name ->
                        name.startsWith("copying") || name.startsWith("copyright") || name.startsWith("license")
                    } }.forEach { file ->
                        nativeCopyFile(file, licenses.resolve(file.relativeTo(libraries.sources).invariantSeparatorsPath.replace("/", "__")))
                    }
                    if (sourceIdentity() != source) throw GradleException("Sources changed during library build; retry")
                    val stage = work.resolve("library-package").apply { mkdirs() }
                    nativeCopyTree(libraries.prefix, stage.resolve("prefix"))
                    nativeCopyTree(licenses, stage.resolve("licenses"))
                    stage.resolve("prefix-path.txt").writeText(nativePath(libraries.prefix))
                    nativePublish(stage, libraryCache, libraryKey)
                    nativeDelete(stage)
                }
            }
            val packaged = packageBridge(libraries)
            nativeCopyTree(work.resolve("licenses"), packaged.resolve("licenses"))
            nativeCopyFile(libraries.prefix.resolve("ffmpeg-config.h"), packaged.resolve("licenses/ffmpeg-config.h"))
            if (sourceIdentity() != source) throw GradleException("Sources changed during native build; retry")
            nativeWriteJson(packaged.resolve("build-info.json"), identity)
            nativePublish(packaged, bundle, fingerprint)
            // 成功产物已原子发布到缓存，移除工作副本，避免每个新指纹留下整份 FFmpeg 源码和对象。
            // 清理失败只是残留磁盘空间，不能否决已经发布的产物。
            nativeDeleteBestEffort(work, logger)
            bundle
        }
    }

    private fun packageBridge(libraries: NativePlayerLibraries): File {
        val chain = libraries.chain
        val ios = chain.system in listOf("ios", "iossim")
        val sources = if (ios) listOf("bolo_mpv.c", "BoloMpvView.m") else
            listOf("bolo_mpv.c", "bolo_mpv_jni.c") + when (chain.system) {
                "macos" -> listOf("bolo_system_media_macos.m")
                "windows" -> listOf("bolo_system_media_windows.cpp")
                else -> emptyList()
            }
        val include = mutableListOf("-I${nativePath(libraries.prefix.resolve("include"))}", "-I${nativePath(libraries.work.resolve("bridge"))}")
        if (!ios && chain.system != "android") {
            val java = chain.environment["JAVA_HOME"]?.let(::File) ?: File(System.getProperty("java.home"))
            val system = if (chain.system == "windows") "win32" else if (chain.system == "macos") "darwin" else "linux"
            if (!java.resolve("include/jni.h").isFile) throw GradleException("JAVA_HOME must point to a JDK with JNI headers: $java")
            include += listOf("-I${nativePath(java.resolve("include"))}", "-I${nativePath(java.resolve("include/$system"))}")
        }
        val objects = sources.map { name ->
            val output = libraries.work.resolve("$name.o")
            libraries.run(listOf(if (name.endsWith(".cpp")) chain.cpp else chain.cc) + libraries.flags + include + listOf(
                when { name.endsWith(".m") -> "-fobjc-arc"; name.endsWith(".cpp") -> "-std=c++17"; else -> "-std=c11" },
                "-O2", "-c", libraries.work.resolve("bridge/$name"), "-o", output))
            output
        }
        val packageDir = libraries.work.resolve("package").apply { mkdirs() }
        val flags = nativeShellSplit(libraries.run("pkg-config", "--static", "--libs", "mpv", capture = true).trim())
        if (ios) {
            val framework = packageDir.resolve("BoloNativePlayer.framework")
            framework.resolve("Headers").mkdirs()
            framework.resolve("Modules").mkdirs()
            val archives = flags.filter { it.startsWith("-l") }.map {
                libraries.prefix.resolve("lib/" + if (it.startsWith("-l:")) it.drop(3) else "lib${it.drop(2)}.a")
            }.filter(File::isFile).distinct()
            if (archives.isEmpty()) throw GradleException("mpv static dependency list is empty")
            libraries.run(listOf("xcrun", "libtool", "-static", "-o", framework.resolve("BoloNativePlayer")) + objects + archives)
            for (name in listOf("bolo_mpv.h", "BoloMpvView.h")) nativeCopyFile(libraries.work.resolve("bridge/$name"), framework.resolve("Headers/$name"))
            framework.resolve("Headers/BoloNativePlayer.h").writeText("#import <Foundation/Foundation.h>\n#import \"bolo_mpv.h\"\n#import \"BoloMpvView.h\"\n")
            framework.resolve("Modules/module.modulemap").writeText("framework module BoloNativePlayer {\n umbrella header \"BoloNativePlayer.h\"\n export *\n module * { export * }\n}\n")
            framework.resolve("Info.plist").writeText("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0"><dict>
                <key>CFBundleIdentifier</key><string>tv.hsrui.bolo.native-player</string>
                <key>CFBundleName</key><string>BoloNativePlayer</string>
                <key>CFBundlePackageType</key><string>FMWK</string>
                <key>CFBundleVersion</key><string>1</string>
                <key>CFBundleShortVersionString</key><string>1.0</string>
                <key>MinimumOSVersion</key><string>16.0</string>
                <key>CFBundleSupportedPlatforms</key><array><string>${if (chain.system == "iossim") "iPhoneSimulator" else "iPhoneOS"}</string></array>
                </dict></plist>
            """.trimIndent() + "\n")
        } else {
            val name = when (chain.system) { "macos" -> "libbolo_mpv.dylib"; "windows" -> "bolo_mpv.dll"; else -> "libbolo_mpv.so" }
            val links = flags.map { if (it.startsWith("-l:")) nativePath(libraries.prefix.resolve("lib/${it.drop(3)}")) else it }
            val mode = if (chain.system == "macos") listOf("-dynamiclib", "-Wl,-install_name,@rpath/$name") else listOf("-shared")
            // ELF 静态依赖在 JNI 库内部绑定，避免 FFmpeg 汇编重定位指向可抢占符号。
            // 禁止未解析符号，防止静态库索引异常被推迟到应用加载时才暴露。
            val symbolFlags = if (chain.system in listOf("android", "linux"))
                listOf("-Wl,--exclude-libs,ALL", "-Wl,--no-undefined") else emptyList()
            val systemLibs = when (chain.system) {
                "macos" -> listOf("-framework", "OpenGL", "-framework", "IOSurface", "-framework", "AppKit", "-framework", "MediaPlayer")
                "windows" -> listOf("-lopengl32", "-lole32", "-lruntimeobject", "-luuid", "-lshlwapi", "-lshcore")
                "linux" -> listOf("-ldl")
                else -> emptyList()
            }
            libraries.run(listOf(chain.cpp) + libraries.flags + chain.link + mode + symbolFlags + listOf("-o", packageDir.resolve(name)) + objects + links + systemLibs)
            if (chain.system == "android") {
                val reader = chain.ar.parentFile.resolve("llvm-readelf" + if (host.windows) ".exe" else "")
                val report = libraries.run(reader, "-lW", packageDir.resolve(name), capture = true)
                val loads = report.lines().map(String::trim).filter { it.startsWith("LOAD ") }
                if (loads.isEmpty() || loads.any { it.split(Regex("\\s+")).last().removePrefix("0x").toLong(16) < 16384 }) {
                    throw GradleException("Android ELF is not aligned to 16 KB: ${chain.target}")
                }
            }
        }
        return packageDir
    }

    private fun prepare(command: String, bundles: Map<String, File>) {
        val key = nativeHash(nativeJson(mapOf("bundles" to bundles.mapValues { it.value.name },
            "podspec" to if (command == "ios") nativeSha(root.resolve("BoloNativePlayer.podspec")) else null)))
        val output = buildDirectory.resolve(command)
        if (nativeValidBundle(output, key)) {
            logger.lifecycle("Native output verified: {}", command)
            return
        }
        val stage = Files.createTempDirectory(buildDirectory.apply { mkdirs() }.toPath(), ".prepare-$command-").toFile()
        try {
            when (command) {
                "android" -> {
                    val mapping = mapOf("android-arm64" to "arm64-v8a", "android-armv7" to "armeabi-v7a", "android-x86" to "x86", "android-x64" to "x86_64")
                    bundles.forEach { (target, bundle) ->
                        val abi = mapping.getValue(target)
                        nativeCopyFile(bundle.resolve("libbolo_mpv.so"), stage.resolve("jniLibs/$abi/libbolo_mpv.so"))
                        nativeCopyTree(bundle.resolve("licenses"), stage.resolve("licenses/$abi"))
                    }
                }
                "ios" -> {
                    host.run("xcodebuild", "-create-xcframework", "-framework", bundles.getValue("ios-arm64").resolve("BoloNativePlayer.framework"),
                        "-framework", bundles.getValue("iossim-arm64").resolve("BoloNativePlayer.framework"), "-output", stage.resolve("BoloNativePlayer.xcframework"))
                    nativeCopyTree(bundles.getValue("ios-arm64").resolve("licenses"), stage.resolve("licenses"))
                    nativeCopyFile(root.resolve("BoloNativePlayer.podspec"), stage.resolve("BoloNativePlayer.podspec"))
                }
                "desktop" -> {
                    val bundle = bundles.values.single()
                    val resources = stage.resolve("resources/bolo-native").apply { mkdirs() }
                    val files = bundle.listFiles().orEmpty().filter { it.extension in listOf("so", "dll", "dylib") }
                    files.forEach { nativeCopyFile(it, resources.resolve(it.name)) }
                    resources.resolve("files.txt").writeText(files.joinToString("\n", postfix = "\n") { nativeSha(it) + "  " + it.name })
                    nativeCopyTree(bundle.resolve("licenses"), resources.resolve("licenses"))
                    nativeCopyFile(bundle.resolve("build-info.json"), resources.resolve("build-info.json"))
                }
            }
            nativePublish(stage, output, key)
        } finally { nativeDeleteBestEffort(stage, logger) }
    }
}

private fun File.resolveSibling(name: String): File = parentFile.resolve(name)
