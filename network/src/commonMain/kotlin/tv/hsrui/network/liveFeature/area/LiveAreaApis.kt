package tv.hsrui.network.liveFeature.area

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.LiveApiUrls
import tv.hsrui.network.wbi.buildWithWbi

suspend fun fetchLiveAreaList(): LiveAreaListResponse {
    return ApiClient.httpClient.get(LiveApiUrls.BASE + LiveApiUrls.AREA_LIST) {
        buildWithWbi {
            parameter("source_id", 1)
        }
        header("Referer", "https://live.bilibili.com/")
    }.body()
}

suspend fun fetchLiveSubAreaList(parentAreaId: Int): LiveSubAreaListResponse {
    return ApiClient.httpClient.get(LiveApiUrls.BASE + LiveApiUrls.SUB_AREA_LIST) {
        parameter("parent_id", parentAreaId)
        parameter("platform", "web")
        header("Referer", "https://live.bilibili.com/")
    }.body()
}

suspend fun fetchLiveAreaRooms(parentAreaId: Int, areaId: Int = 0, page: Int = 1): LiveAreaRoomsResponse {
    return ApiClient.httpClient.get(LiveApiUrls.BASE + LiveApiUrls.AREA_ROOMS) {
        buildWithWbi(addWebID = true) {
            parameter("platform", "web")
            parameter("parent_area_id", parentAreaId)
            parameter("area_id", areaId)
            parameter("page", page)
            parameter("sort_type", "")
            parameter("vajra_business_key", "")
            parameter("web_location", if (parentAreaId == 301) "444.242" else "444.253")
        }
        header("Referer", "https://live.bilibili.com/")
    }.body()
}
