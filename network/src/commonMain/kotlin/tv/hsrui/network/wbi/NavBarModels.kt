package tv.hsrui.network.wbi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NavBarResponse(
    val code: Int = -1,
    val message: String = "",
    val data: NavBarData = NavBarData()
) {
    val isSuccess get() = (code == 0 || code == -101)

    val wbiImgKey get() = data.wbiData.wbiImgUrl.substringAfterLast("/").substringBeforeLast(".")
    val wbiSubKey get() = data.wbiData.wbiSubUrl.substringAfterLast("/").substringBeforeLast(".")

    @Serializable
    data class NavBarData(
        @SerialName("wbi_img") val wbiData: WbiData = WbiData()
    ) {
        @Serializable
        data class WbiData(
            @SerialName("img_url") val wbiImgUrl: String = "",
            @SerialName("sub_url") val wbiSubUrl: String = ""

        )
    }
}