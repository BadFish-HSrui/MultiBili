package tv.hsrui.network.login.storage

import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.invoke
import tv.hsrui.network.model.Cookie

class LoginStorage(private val loginKSafe: KSafe) {
    var cookie by loginKSafe(Cookie())

    val isLoggedIn: Boolean get() = (cookie.sessData.isNotEmpty())

    fun saveCookie(cookieMap: Map<String, String>) {
        cookie = Cookie(
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

    fun getCookieString(): String = buildString {
        append("SESSDATA=${cookie.sessData}; ")
        append("bili_jct=${cookie.biliJct}; ")
        append("DedeUserID=${cookie.dedeUserID}; ")
        append("DedeUserID__ckMd5=${cookie.dedeUserIDCkMd5}; ")
        append("b_nut=${cookie.bNut}; ")
        append("sid=${cookie.sid}; ")
        append("buvid3=${cookie.buvid3}; ")
        append("buvid4=${cookie.buvid4}; ")
        append("buvid_fp=${cookie.buvidFp}")
    }
}
