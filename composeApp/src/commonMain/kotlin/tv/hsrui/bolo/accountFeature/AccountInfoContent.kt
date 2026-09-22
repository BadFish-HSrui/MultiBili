package tv.hsrui.bolo.accountFeature

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.openUserSpace
import tv.hsrui.bolo.ui.components.user.ShowUserInfoLayout
import tv.hsrui.network.feature.account.myinfo.MyAccountInfoManager
import tv.hsrui.network.utils.formatCountToString

@Composable
internal fun AccountInfoContent(modifier: Modifier = Modifier) {
    val myAccountInfoManager: MyAccountInfoManager = koinInject()
    val myAccountInfo by myAccountInfoManager.info.collectAsState()

    LaunchedEffect(Unit) {
        myAccountInfoManager.loadInfo(true)
    }

    ShowUserInfoLayout(
        face = myAccountInfo.face,
        name = myAccountInfo.name,
        sign = myAccountInfo.sign,
        level = myAccountInfo.level,
        levelString = myAccountInfo.levelString,
        isVip = myAccountInfo.isVip,
        vipTypeString = myAccountInfo.vipTypeString,
        modifier = modifier,
        onUserClick = if (myAccountInfo.mid > 0) {
            { openUserSpace(myAccountInfo.mid) }
        } else {
            null
        },
    ) {
        Text(
            text = "硬币: ${myAccountInfo.coins}",
            modifier = Modifier.weight(1F).alpha(0.8F),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "关注: ${myAccountInfo.following.formatCountToString()}",
            modifier = Modifier.weight(1F).alpha(0.8F),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "粉丝: ${myAccountInfo.follower.formatCountToString()}",
            modifier = Modifier.weight(1F).alpha(0.8F),
            textAlign = TextAlign.Center,
        )
    }
}