package tv.hsrui.network.login.storage

import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.invoke
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.mp.KoinPlatformTools
import tv.hsrui.network.model.Cookies

class LoginStorage(private val loginKSafe: KSafe) {
    var cookies by loginKSafe(Cookies(), key = "cookies")

    val isLoggedIn: Boolean get() = (cookies.sessData.isNotEmpty())
    val isLoggedInFlow = loginKSafe.getFlow("cookies", Cookies())
        .map { it.sessData.isNotEmpty() }
        .distinctUntilChanged()
    val hasCookies: Boolean
        get() = cookies.run {
            dedeUserIDCkMd5.isNotEmpty() ||
                dedeUserID != 0L ||
                sessData.isNotEmpty() ||
                biliJct.isNotEmpty() ||
                bNut != 0L ||
                sid.isNotEmpty() ||
                buvidFp.isNotEmpty() ||
                buvid3.isNotEmpty() ||
                buvid4.isNotEmpty()
        }

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

    suspend fun clearLoginCookies() {
        val saved = loginKSafe.get("cookies", Cookies())
        // 等待落盘成功，不能用异步属性赋值提前报告退出完成。
        loginKSafe.put("cookies", saved.copy(
            dedeUserIDCkMd5 = "",
            dedeUserID = 0,
            sessData = "",
            biliJct = "",
            sid = "",
        ))
    }

    fun saveBuvid3(buvid3: String) {
        cookies = cookies.copy(buvid3 = buvid3)
    }

    fun getCookiesString(): String = buildList {
        cookies.run {
            if (sessData.isNotEmpty()) add("SESSDATA=$sessData")
            if (biliJct.isNotEmpty()) add("bili_jct=$biliJct")
            if (dedeUserID != 0L) add("DedeUserID=$dedeUserID")
            if (dedeUserIDCkMd5.isNotEmpty()) add("DedeUserID__ckMd5=$dedeUserIDCkMd5")
            if (bNut != 0L) add("b_nut=$bNut")
            if (sid.isNotEmpty()) add("sid=$sid")
            if (buvid3.isNotEmpty()) add("buvid3=$buvid3")
            if (buvid4.isNotEmpty()) add("buvid4=$buvid4")
            if (buvidFp.isNotEmpty()) add("buvid_fp=$buvidFp")
        }
    }.joinToString("; ")
}

fun isLoggedIn(): Boolean = KoinPlatformTools.defaultContext().get().get<LoginStorage>().isLoggedIn
