package tv.hsrui.bolo

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform