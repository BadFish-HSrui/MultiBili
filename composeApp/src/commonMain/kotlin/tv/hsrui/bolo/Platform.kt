package tv.hsrui.bolo

enum class PlatformType {
    Ios,
    Android,
    Desktop
}

data class Platform(
    val deviceCode: String,
    val type: PlatformType,
    val jvmRuntimeDescription: String? = null
)

expect fun getPlatform(): Platform