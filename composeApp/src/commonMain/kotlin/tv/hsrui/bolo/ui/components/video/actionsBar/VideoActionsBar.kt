package tv.hsrui.bolo.ui.components.video.actionsBar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import tv.hsrui.bolo.download.DownloadTarget
import tv.hsrui.network.feature.video.CopyrightType
import tv.hsrui.network.feature.video.VideoInfoData
import tv.hsrui.network.feature.video.actions.state.VideoActionsStateResponse
import tv.hsrui.network.feature.video.actions.state.fetchVideoActionsStateFor
import tv.hsrui.network.login.storage.isLoggedIn
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun VideoActionsBar(videoInfo: VideoInfoData, modifier: Modifier = Modifier) {
    var actionsState by rememberSerializable { mutableStateOf(VideoActionsStateResponse()) }
    var trigger by rememberSaveable { mutableStateOf(0) }
    val reloadState: suspend () -> Unit = {
        /*
        TODO:
           潜在问题是B站服务器内部状态同步延迟,
           直接重新加载会获取到旧数据,
           暂时使用100ms延迟解决,
           未来可能改为使用乐观更新+延迟加载验证状态.
        */
        delay(100.milliseconds)
        trigger++
    }
    val isLogin = isLoggedIn()

    LaunchedEffect(trigger) {
        if (isLogin) {
            // 首次请求失败后最多重试三次，耗尽后静默保留当前状态。
            repeat(4) {
                val result = try {
                    fetchVideoActionsStateFor(videoInfo.bvid)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                currentCoroutineContext().ensureActive()
                if (result?.isSuccess == true) {
                    actionsState = result
                    return@LaunchedEffect
                }
            }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LikeButton(
            avid = videoInfo.avid,
            likeCount = videoInfo.stateCount.like.toLong(),
            isLiked = actionsState.isLiked,
            canClick = isLogin,
            reloadState = reloadState
        )
        CoinButton(
            avid = videoInfo.avid,
            coinCount = videoInfo.stateCount.coin.toLong(),
            coinLimit = if (videoInfo.copyrightType == CopyrightType.Reprint) 1 else 2,
            isCoined = actionsState.isCoined,
            coinedCount = actionsState.coinedCount,
            canClick = isLogin,
            reloadState = reloadState
        )
        FavoriteButton(
            avid = videoInfo.avid,
            favoriteCount = videoInfo.stateCount.favorite.toLong(),
            isFavorite = actionsState.isFavorite,
            canClick = isLogin,
            reloadState = reloadState
        )
        DownloadButton(
            id = videoInfo.avid,
            cid = videoInfo.cid,
            title = videoInfo.title,
            canClick = isLogin,
            targets = videoInfo.parts.takeIf { it.size > 1 }?.mapIndexed { index, part ->
                DownloadTarget(videoInfo.avid, part.cid, part.title.ifBlank { "P${part.pageNumber}" },
                    part.pageNumber.takeIf { it > 0 } ?: (index + 1))
            } ?: listOf(DownloadTarget(videoInfo.avid, videoInfo.parts.firstOrNull()?.cid ?: videoInfo.cid, "", 1)),
        )
    }
}
