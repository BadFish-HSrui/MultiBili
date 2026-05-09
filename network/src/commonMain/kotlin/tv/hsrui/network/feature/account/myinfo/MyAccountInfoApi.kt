package tv.hsrui.network.feature.account.myinfo

import io.ktor.client.call.body
import io.ktor.client.request.get
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.login.storage.LoginStorage

suspend fun fetchMyAccountInfo(): MyAccountInfo {
    val loginStorage: LoginStorage = getKoin().get()
    val result: MyAccountInfo
    if (loginStorage.isLoggedIn) {
        val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.MY_ACCOUNT_INFO)
        result = response.body()
    } else {
        result = MyAccountInfo()
    }
    return result
}