package tv.hsrui.bolo

actual fun getPlatform(): Platform = Platform(
    deviceCode = "${System.getProperty("os.name")}-${System.getProperty("os.version")} (${System.getProperty("os.arch")})",
    type = PlatformType.Desktop,
    jvmRuntimeDescription = listOfNotNull(
        System.getProperty("java.vendor")?.takeIf { it.isNotBlank() },
        (System.getProperty("java.version")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("java.runtime.version")?.takeIf { it.isNotBlank() })
            ?.substringBefore('+')
            ?.substringBefore('-')
            ?: "未知版本"
    ).joinToString(" ")
)