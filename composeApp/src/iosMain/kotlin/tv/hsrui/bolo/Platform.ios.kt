package tv.hsrui.bolo

import kotlinx.cinterop.memScoped
import kotlinx.cinterop.*
import platform.posix.uname
import platform.posix.utsname
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPhone

@OptIn(ExperimentalForeignApi::class)
actual fun getPlatform(): Platform = Platform(
    deviceCode = memScoped {
        val utsname = alloc<utsname>()
        uname(utsname.ptr)
        utsname.machine.toKString()
    },
    type = PlatformType.Ios,
    isPhone = UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPhone,
)
