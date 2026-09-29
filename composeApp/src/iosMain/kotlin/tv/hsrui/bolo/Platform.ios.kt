package tv.hsrui.bolo

import kotlinx.cinterop.memScoped
import kotlinx.cinterop.*
import platform.posix.uname
import platform.posix.utsname

@OptIn(ExperimentalForeignApi::class)
actual fun getPlatform(): Platform = Platform(
    deviceCode = memScoped {
        val utsname = alloc<utsname>()
        uname(utsname.ptr)
        utsname.machine.toKString()
    },
    type = PlatformType.Ios
)