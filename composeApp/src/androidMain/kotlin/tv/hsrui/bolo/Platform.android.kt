package tv.hsrui.bolo

import android.os.Build

actual fun getPlatform(): Platform = Platform(
    deviceCode = "${Build.MANUFACTURER} ${Build.MODEL}",
    type = PlatformType.Android
)