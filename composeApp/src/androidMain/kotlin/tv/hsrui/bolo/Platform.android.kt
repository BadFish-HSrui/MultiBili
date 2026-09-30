package tv.hsrui.bolo

import android.os.Build
import tv.hsrui.bolo.utils.url.AppContext
import kotlin.math.max
import kotlin.math.min

actual fun getPlatform(): Platform = Platform(
    deviceCode = "${Build.MANUFACTURER} ${Build.MODEL}",
    type = PlatformType.Android,
    isPhone = AppContext.instance.resources.configuration.let {
        min(it.screenWidthDp, it.screenHeightDp) < 600 && max(it.screenWidthDp, it.screenHeightDp) < 840
    },
)
