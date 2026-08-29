package tv.hsrui.network.login

import io.ktor.client.call.body
import io.ktor.client.request.get
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.login.storage.LoginStorage

suspend fun ensureBuvid3() {
    val loginStorage: LoginStorage = getKoin().get()
    if (loginStorage.cookies.buvid3.isNotEmpty()) return

    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.BUVID3)
    val raw: RawBuvid3Response = response.body()
    val buvid3 = raw.data?.buvid.orEmpty()
    check(raw.code == 0 && buvid3.isNotEmpty()) {
        "获取匿名请求标识失败（code=${raw.code}）"
    }
    loginStorage.saveBuvid3(buvid3)
}
