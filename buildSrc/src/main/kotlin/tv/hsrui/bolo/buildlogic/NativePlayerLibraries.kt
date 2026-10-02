package tv.hsrui.bolo.buildlogic

import org.gradle.api.GradleException
import java.io.File

internal class NativePlayerLibraries(
    private val owner: NativePlayerBuild,
    val chain: NativePlayerToolchain,
    val work: File,
    val jobs: Int,
) {
    val prefix = work.resolve("prefix").apply { mkdirs() }
    val sources = work.resolve("sources").apply { mkdirs() }
    val flags = chain.flags + "-ffile-prefix-map=${nativePath(work)}=/bolo-native"
    val cross = work.resolve("cross.ini")
    val environment = chain.environment.toMutableMap().apply {
        put("PKG_CONFIG_PATH", "")
        val systemPc = if (chain.system == "linux") File.pathSeparator +
            chain.host.run("pkg-config", "--variable=pc_path", "pkg-config", capture = true).trim() else ""
        put("PKG_CONFIG_LIBDIR", nativePath(prefix.resolve("lib/pkgconfig")) + systemPc)
    }

    init { chain.crossFile(cross, flags) }

    fun run(vararg args: Any, cwd: File = work, capture: Boolean = false): String =
        chain.host.run(args.toList(), cwd, environment, capture)
    fun run(args: List<Any>, cwd: File = work, capture: Boolean = false): String =
        chain.host.run(args, cwd, environment, capture)

    private fun meson(name: String, options: List<String>) {
        val source = sources.resolve(name)
        if (name == "mpv") owner.sourceCopy(name, source) else owner.dependencySource(name, source)
        if (name == "libplacebo") {
            for ((dependency, folder) in mapOf("vulkan-headers" to "Vulkan-Headers", "fast-float" to "fast_float")) {
                val path = source.resolve("3rdparty/$folder")
                if (path.exists() && !path.delete()) throw GradleException("Expected empty dependency directory: $path")
                owner.dependencySource(dependency, path)
            }
        }
        val directory = work.resolve(name)
        run(listOf("meson", "setup", directory, source, "--prefix=${nativePath(prefix)}", "--libdir=lib",
            "--buildtype=release", "--default-library=static", "--wrap-mode=nodownload",
            "--cross-file=${nativePath(cross)}", "--native-file=${nativePath(work.resolve("native.ini"))}",
            "-Db_staticpic=true", "-Dauto_features=disabled") + options)
        run("meson", "compile", "-C", directory, "-j", jobs)
        run("meson", "install", "-C", directory)
    }

    fun compile() {
        meson("freetype", listOf("-Dzlib=internal"))
        meson("harfbuzz", listOf("-Dfreetype=enabled", "-Dtests=disabled", "-Dutilities=disabled"))
        meson("fribidi", listOf("-Ddocs=false", "-Dbin=false", "-Dtests=false"))
        meson("libass", listOf("-Drequire-system-font-provider=false"))
        meson("dav1d", listOf("-Denable_tools=false", "-Denable_tests=false"))
        meson("libplacebo", listOf("-Ddemos=false", "-Dtests=false"))
        val mbedTls = chain.system in listOf("android", "linux")
        if (mbedTls) {
            val source = sources.resolve("mbedtls")
            owner.dependencySource("mbedtls", source)
            val framework = source.resolve("framework")
            if (framework.exists() && !framework.delete()) throw GradleException("Expected empty Mbed TLS framework directory")
            owner.dependencySource("mbedtls-framework", framework)
            val directory = work.resolve("mbedtls")
            run("cmake", "-S", source, "-B", directory, "-G", "Ninja", "-DCMAKE_BUILD_TYPE=Release",
                "-DCMAKE_INSTALL_PREFIX=${nativePath(prefix)}", "-DCMAKE_C_COMPILER=${nativePath(chain.cc)}",
                "-DCMAKE_C_FLAGS=${flags.joinToString(" ", transform = ::nativeQuote)}",
                "-DCMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY", "-DCMAKE_SYSTEM_NAME=Linux",
                "-DCMAKE_AR=${nativePath(chain.ar)}", "-DCMAKE_RANLIB=${nativePath(chain.ranlib)}",
                "-DPython3_EXECUTABLE=${nativePath(chain.host.tool("python3"))}",
                "-DENABLE_PROGRAMS=OFF", "-DENABLE_TESTING=OFF", "-DUSE_SHARED_MBEDTLS_LIBRARY=OFF", "-DCMAKE_POSITION_INDEPENDENT_CODE=ON")
            run("cmake", "--build", directory, "--parallel", jobs)
            run("cmake", "--install", directory)
        }
        val ff = sources.resolve("ffmpeg")
        owner.sourceCopy("ffmpeg", ff)
        val directory = work.resolve("ffmpeg").apply { mkdirs() }
        val ffFlags = flags + "-I${nativePath(prefix.resolve("include"))}"
        val ffLink = chain.link + "-L${nativePath(prefix.resolve("lib"))}"
        val options = mutableListOf<Any>("bash", ff.resolve("configure").relativeTo(directory).invariantSeparatorsPath,
            "--prefix=${nativePath(prefix)}", "--enable-cross-compile", "--arch=${chain.machine.getValue("cpu_family")}",
            "--target-os=${if (chain.system == "windows") "mingw32" else chain.machine.getValue("system")}",
            "--cc=${nativePath(chain.cc)}", "--cxx=${nativePath(chain.cpp)}", "--ar=${nativePath(chain.ar)}", "--strip=${nativePath(chain.strip)}",
            "--extra-cflags=${ffFlags.joinToString(" ", transform = ::nativeQuote)}",
            "--extra-ldflags=${ffLink.joinToString(" ", transform = ::nativeQuote)}",
            "--pkg-config=${nativePath(chain.host.tool("pkg-config"))}", "--pkg-config-flags=--static", "--disable-autodetect",
            "--disable-programs", "--disable-doc", "--disable-debug", "--disable-avdevice", "--disable-encoders", "--disable-muxers", "--enable-muxer=mp4",
            "--disable-gpl", "--disable-nonfree", "--enable-static", "--disable-shared", "--enable-pic", "--enable-libdav1d", "--enable-network")
        options.addAll(when {
            mbedTls -> listOf("--enable-mbedtls", "--enable-version3")
            chain.system == "windows" -> listOf("--enable-schannel", "--enable-d3d11va", "--enable-dxva2")
            else -> listOf("--enable-securetransport", "--enable-videotoolbox", "--enable-audiotoolbox")
        })
        if (chain.system == "android") options.addAll(listOf("--enable-jni", "--enable-mediacodec"))
        // FFmpeg 的 32 位 x86 手写汇编包含 Android 不允许的文本重定位。
        if (chain.target == "android-x86") options += "--disable-asm"
        if (chain.system == "linux") options += "--enable-vaapi"
        run(options, cwd = directory)
        run("make", "-j$jobs", cwd = directory)
        run("make", "install", cwd = directory)
        val mpv = listOf("-Dgpl=false", "-Dcplayer=false", "-Dlibmpv=true", "-Dbuild-date=false", "-Dlua=disabled", "-Dgl=enabled", "-Dplain-gl=enabled") +
            when (chain.system) {
                "android" -> listOf("-Degl-android=enabled", "-Daudiotrack=enabled", "-Dandroid-media-ndk=enabled")
                "ios", "iossim" -> listOf("-Dios-gl=enabled", "-Daudiounit=enabled")
                "macos" -> listOf("-Dcocoa=enabled", "-Dgl-cocoa=enabled", "-Dvideotoolbox-gl=enabled", "-Dcoreaudio=enabled", "-Dswift-build=disabled")
                "windows" -> listOf("-Dwasapi=enabled", "-Dd3d-hwaccel=enabled", "-Dwin32-threads=enabled")
                else -> listOf("-Dpulse=enabled", "-Dalsa=enabled", "-Ddrm=enabled", "-Dvaapi=enabled", "-Dvaapi-drm=enabled")
            }
        meson("mpv", mpv)
        nativeCopyFile(directory.resolve("config.h"), prefix.resolve("ffmpeg-config.h"))
    }
}
