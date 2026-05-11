package tv.hsrui.network.feature.account.myinfo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MyAccountInfo(
    val code: Int = -1,
    val message: String = "",
    private val data: MyAccountData = MyAccountData(),

    val following: Int = -1,
    val follower: Int = -1
){
    val isSuccess get() = (code == 0)

    val coins by data::coins

    val mid by data.profile::mid
    val name by data.profile::name
    val face by data.profile::face
    val sign by data.profile::sign
    val sex by data.profile::sex
    val level get() = if (data.profile.isSeniorMember == 1) 7 else data.profile.level
    val levelString get() = if (data.profile.isSeniorMember == 1) "Lv.6+" else "Lv.${data.profile.level}"

    val isVip: Boolean get() = (data.profile.vip.status == 1)
    val vipTypeString: String get() = when(data.profile.vip.type) {
        1 -> "大会员"
        2 -> "年度大会员"
        else -> ""
    }
}

@Serializable
data class MyAccountData(
    val profile: MyAccountProfile = MyAccountProfile(),
    val coins: Float = -1F
)

@Serializable
data class MyAccountProfile(
    val mid: Long = -1,
    val name: String = "",
    val sex: String = "",
    val face: String = "",
    val sign: String = "",
    val level: Int = 0,
    val vip: MyAccountVip = MyAccountVip(),
    @SerialName("is_senior_member") val isSeniorMember: Int = 0
)

@Serializable
data class MyAccountVip(
    val type: Int = 0,
    val status: Int = 0
)