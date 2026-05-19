package tv.hsrui.bolo

import android.os.Build

actual fun getPlatform(): Platform = Platform(
    name = "Android ${Build.VERSION.SDK_INT}",
    deviceCode = "${Build.MANUFACTURER} ${Build.MODEL}",
    type = PlatformType.Android
)