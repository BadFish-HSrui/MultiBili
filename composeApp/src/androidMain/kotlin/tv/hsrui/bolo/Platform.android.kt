package tv.hsrui.bolo

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    override val type = PlatformType.Android
}

actual fun getPlatform(): Platform = AndroidPlatform()