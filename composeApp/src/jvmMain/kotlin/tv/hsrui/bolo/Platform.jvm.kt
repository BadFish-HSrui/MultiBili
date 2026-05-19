package tv.hsrui.bolo

actual fun getPlatform(): Platform = Platform(
    name = "Java ${System.getProperty("java.version")}",
    deviceCode = "${System.getProperty("os.name")}-${System.getProperty("os.version")} (${System.getProperty("os.arch")})",
    type = PlatformType.Desktop
)