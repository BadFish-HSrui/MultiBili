package tv.hsrui.bolo.accountFeature

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import tv.hsrui.bolo.ui.constant.color.BiliColor
import tv.hsrui.bolo.ui.constant.color.BiliColor.getLevelColor
import tv.hsrui.bolo.utils.formatToString
import tv.hsrui.network.feature.account.myinfo.MyAccountInfoManager
import kotlin.text.ifEmpty

@Composable
internal fun AccountInfoContent(modifier: Modifier = Modifier) {
    val myAccountInfoManager: MyAccountInfoManager = koinInject()
    val myAccountInfo by myAccountInfoManager.info.collectAsState()

    LaunchedEffect(Unit) {
        myAccountInfoManager.loadInfo(true)
    }

    Card(modifier.wrapContentHeight().fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 8.dp, bottom = 4.dp, start = 8.dp, end = 8.dp)
        ) {
            Column(modifier = Modifier.width(64.dp)) {
                AsyncImage(
                    model = myAccountInfo.face,
                    contentDescription = "个人头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1F)
                        .clip(CircleShape)
                )
                if (myAccountInfo.isVip) {
                    Surface(
                        shape = CircleShape,
                        color = Color(BiliColor.BIG),
                        modifier = Modifier.width(68.dp)
                    ) {
                        Text(
                            text = myAccountInfo.vipTypeString,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(start = 8.dp).wrapContentHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = getLevelColor(myAccountInfo.level)
                    ) {
                        Text(
                            text = myAccountInfo.levelString,
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    Text(
                        text = myAccountInfo.name,
                        textAlign = TextAlign.Center,
                        color = if (myAccountInfo.isVip) Color(BiliColor.BIG) else LocalContentColor.current,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                }
                Text(
                    text = myAccountInfo.sign.ifEmpty { "这个人很懒，没有签名喵" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .alpha(0.75F)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 2.dp)
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.labelMedium
                ) {
                    Row {
                        Text(
                            text = "硬币: ${myAccountInfo.coins}",
                            modifier = Modifier.weight(1F).alpha(0.8F),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "关注: ${myAccountInfo.following.formatToString()}",
                            modifier = Modifier.weight(1F).alpha(0.8F),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "粉丝: ${myAccountInfo.follower.formatToString()}",
                            modifier = Modifier.weight(1F).alpha(0.8F),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}