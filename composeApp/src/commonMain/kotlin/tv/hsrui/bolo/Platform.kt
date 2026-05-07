package tv.hsrui.bolo

enum class PlatformType{
    Ios,
    Android,
    Desktop
}

interface Platform {
    val name: String
    val type: PlatformType
}

expect fun getPlatform(): Platform