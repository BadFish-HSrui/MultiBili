package tv.hsrui.network.feature.account.myinfo

import kotlinx.serialization.Serializable

@Serializable
data class MyAccountInfo(
    val code: Int = -1,
    val message: String = "",
    private val data: MyAccountData = MyAccountData()
){
    val isSuccess get() = (code == 0)

    val coins by data::coins

    val min by data.profile::mid
    val name by data.profile::name
    val face by data.profile::face
    val sex by data.profile::sex
    val level by data.profile::level

    val isVip: Boolean get() = (data.profile.vip.status == 1)
}

@Serializable
data class MyAccountData(
    val profile: MyAccountProfile = MyAccountProfile(),
    val coins: Int = -1
)

@Serializable
data class MyAccountProfile(
    val mid: Long = -1,
    val name: String = "",
    val sex: String = "",
    val face: String = "",
    val level: Int = 0,
    val vip: MyAccountVip = MyAccountVip()
)

@Serializable
data class MyAccountVip(
    val type: Int = 0,
    val status: Int = 0
)