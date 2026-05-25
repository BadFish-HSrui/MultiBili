package tv.hsrui.network.login.storage

import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.invoke
import org.koin.mp.KoinPlatformTools
import tv.hsrui.network.model.Cookies

class LoginStorage(private val loginKSafe: KSafe) {
    var cookies by loginKSafe(Cookies())

    val isLoggedIn: Boolean get() = (cookies.sessData.isNotEmpty())

    fun saveCookie(cookieMap: Map<String, String>) {
        cookies = Cookies(
            dedeUserIDCkMd5 = cookieMap["DedeUserID__ckMd5"] ?: "",
            dedeUserID = cookieMap["DedeUserID"]?.toLongOrNull() ?: 0,
            sessData = cookieMap["SESSDATA"] ?: "",
            biliJct = cookieMap["bili_jct"] ?: "",
            bNut = cookieMap["b_nut"]?.toLongOrNull() ?: 0,
            sid = cookieMap["sid"] ?: "",
            buvidFp = cookieMap["buvid_fp"] ?: "",
            buvid3 = cookieMap["buvid3"] ?: "",
            buvid4 = cookieMap["buvid4"] ?: ""
        )
    }

    fun getCookiesString(): String = buildString {
        append("SESSDATA=${cookies.sessData}; ")
        append("bili_jct=${cookies.biliJct}; ")
        append("DedeUserID=${cookies.dedeUserID}; ")
        append("DedeUserID__ckMd5=${cookies.dedeUserIDCkMd5}; ")
        append("b_nut=${cookies.bNut}; ")
        append("sid=${cookies.sid}; ")
        append("buvid3=${cookies.buvid3}; ")
        append("buvid4=${cookies.buvid4}; ")
        append("buvid_fp=${cookies.buvidFp}")
    }
}

fun isLoggedIn(): Boolean = KoinPlatformTools.defaultContext().get().get<LoginStorage>().isLoggedIn