package tv.hsrui.network.feature.user.info

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserInfoResponse(
    val code: Int = -1,
    val message: String = "",
    val data: UserInfoData? = null,
) {
    val isSuccess get() = code == 0 && data != null && data.mid > 0
}

@Serializable
data class UserInfoData(
    val mid: Long = -1,
    val name: String = "",
    val face: String = "",
    val sign: String = "",
    @SerialName("level") private val levelValue: Int = 0,
    @SerialName("is_senior_member") private val isSeniorMember: Int = 0,
    private val vip: UserVipData = UserVipData(),
) {
    val level get() = if (isSeniorMember == 1) 7 else levelValue
    val levelString get() = if (isSeniorMember == 1) "Lv.6+" else "Lv.$levelValue"
    val isVip get() = vip.isVip
    val vipTypeString get() = vip.typeString
}

@Serializable
data class UserVipData(
    @SerialName("type") private val typeCode: Int = 0,
    @SerialName("status") private val statusCode: Int = 0,
) {
    val isVip get() = statusCode == 1
    val typeString get() = when (typeCode) {
        1 -> "大会员"
        2 -> "年度大会员"
        else -> ""
    }
}

@Serializable
data class UserUpStatResponse(
    val code: Int = -1,
    val message: String = "",
    val data: UserUpStatData? = null,
) {
    val isSuccess get() = code == 0 && data != null
}

@Serializable
data class UserUpStatData(
    @SerialName("likes") val likeCount: Long? = null,
    private val archive: UserArchiveStatData? = null,
) {
    val playCount get() = archive?.playCount
}

@Serializable
data class UserArchiveStatData(
    @SerialName("view") val playCount: Long? = null,
)
