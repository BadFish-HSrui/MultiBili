package tv.hsrui.bolo

enum class PlatformType {
    Ios,
    Android,
    Desktop
}

data class Platform(
    val name: String,
    val deviceCode: String,
    val type: PlatformType
)

expect fun getPlatform(): Platform