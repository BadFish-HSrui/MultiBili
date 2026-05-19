package tv.hsrui.bolo

import kotlinx.cinterop.memScoped
import kotlinx.cinterop.*
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname

@OptIn(ExperimentalForeignApi::class)
actual fun getPlatform(): Platform = Platform(
    name = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion,
    deviceCode = memScoped {
        val utsname = alloc<utsname>()
        uname(utsname.ptr)
        utsname.machine.toKString()
    },
    type = PlatformType.Ios
)