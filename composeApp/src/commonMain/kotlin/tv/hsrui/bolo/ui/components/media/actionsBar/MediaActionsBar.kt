package tv.hsrui.bolo.ui.components.media.actionsBar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.koin.compose.koinInject
import tv.hsrui.bolo.download.DownloadType
import tv.hsrui.bolo.download.DownloadTarget
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.components.video.actionsBar.CoinButton
import tv.hsrui.bolo.ui.components.video.actionsBar.DownloadButton
import tv.hsrui.bolo.ui.components.video.actionsBar.FavoriteButton
import tv.hsrui.bolo.ui.components.video.actionsBar.LikeButton
import tv.hsrui.network.feature.media.MediaEpisode
import tv.hsrui.network.feature.media.MediaSeasonData
import tv.hsrui.network.feature.media.actions.MediaActionsStateResponse
import tv.hsrui.network.feature.media.actions.fetchMediaActionsState
import tv.hsrui.network.feature.media.actions.fetchMediaCoinLimit
import tv.hsrui.network.login.storage.LoginStorage
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MediaActionsBar(episode: MediaEpisode, media: MediaSeasonData, modifier: Modifier = Modifier) {
    val loginStorage: LoginStorage = koinInject()
    val snackbarManager: SnackbarManager = koinInject()
    val isLogin by loginStorage.isLoggedInFlow.collectAsState(initial = loginStorage.isLoggedIn)
    val accountMid by loginStorage.currentUserMidFlow.collectAsState(
        initial = if (loginStorage.isLoggedIn) loginStorage.cookies.dedeUserID else 0L,
    )

    key(episode.episodeId, episode.avid, accountMid, isLogin) {
        var actionsState by remember { mutableStateOf<MediaActionsStateResponse?>(null) }
        var coinLimit by remember { mutableStateOf<Int?>(null) }
        var trigger by remember { mutableIntStateOf(0) }
        val reloadState: suspend () -> Unit = {
            delay(100.milliseconds)
            trigger++
        }

        LaunchedEffect(trigger) {
            if (episode.episodeId <= 0) return@LaunchedEffect
            try {
                val result = fetchMediaActionsState(episode.episodeId)
                currentCoroutineContext().ensureActive()
                if (result.isSuccess) {
                    actionsState = result
                } else {
                    snackbarManager.showMessage(result.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbarManager.showMessage(e.message ?: "剧集操作状态加载失败")
            }
        }

        LaunchedEffect(trigger) {
            if (!isLogin || !episode.isAvailable) return@LaunchedEffect
            coinLimit = null
            try {
                val result = fetchMediaCoinLimit(episode.episodeId)
                currentCoroutineContext().ensureActive()
                if (result.isSuccess) {
                    coinLimit = result.coinLimit
                } else {
                    snackbarManager.showMessage(result.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbarManager.showMessage(e.message ?: "剧集投币上限加载失败")
            }
        }

        val canClick = isLogin && episode.isAvailable && actionsState?.hasUserCommunity == true
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            LikeButton(
                avid = episode.avid,
                likeCount = actionsState?.likeCount,
                isLiked = actionsState?.isLiked == true,
                canClick = canClick,
                reloadState = reloadState,
            )
            CoinButton(
                avid = episode.avid,
                coinCount = actionsState?.coinCount,
                coinLimit = coinLimit ?: 0,
                isCoined = (actionsState?.coinedCount ?: 0) > 0,
                coinedCount = actionsState?.coinedCount ?: 0,
                canClick = canClick && coinLimit != null,
                reloadState = reloadState,
            )
            FavoriteButton(
                avid = episode.avid,
                favoriteCount = actionsState?.favoriteCount,
                isFavorite = actionsState?.isFavorite == true,
                canClick = canClick,
                reloadState = reloadState,
                resourceType = 42,
            )
            DownloadButton(
                id = episode.episodeId,
                cid = episode.cid,
                title = media.title,
                canClick = isLogin && episode.isAvailable,
                type = DownloadType.Media,
                groupId = media.seasonId,
                targets = media.episodes.mapIndexedNotNull { index, item ->
                    if (item.isAvailable) DownloadTarget(item.episodeId, item.cid, item.displayTitle, index + 1) else null
                },
            )
        }
    }
}
