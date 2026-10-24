package tv.hsrui.bolo.livePlayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.livePlayer.controls.LivePlayerControls
import tv.hsrui.bolo.player.PlayerFullscreenState
import tv.hsrui.bolo.player.base.BoloVideoPlayer
import tv.hsrui.bolo.ui.components.error.ShowErrorContent

@Composable
fun LivePlayer(
    viewModel: LivePlayerViewModel,
    fullscreenState: PlayerFullscreenState,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val controller = viewModel.playbackSession?.controller
    val playback = controller?.state?.collectAsState()?.value
    val success = uiState as? LivePlayerUiState.Success
    val error = (uiState as? LivePlayerUiState.Error)?.message ?: playback?.error
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (controller != null) {
            BoloVideoPlayer(controller = controller, modifier = Modifier.matchParentSize())
        }
        when {
            uiState is LivePlayerUiState.Offline -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (viewModel.broadcastEnded) "直播已结束" else "当前直播间未开播", color = Color.White)
            }
            error != null -> Surface(Modifier.matchParentSize(), color = Color.Black, contentColor = Color.White) {
                ShowErrorContent(message = error, retry = viewModel::loadPlayback)
            }
            uiState is LivePlayerUiState.Loading || success?.isPreparing == true || playback?.isBuffering == true ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            playback?.isEnded == true && viewModel.broadcastEnded -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("直播已结束", color = Color.White)
            }
        }
        LivePlayerControls(
            viewModel = viewModel,
            fullscreenState = fullscreenState,
            navigationOnly = success == null || controller == null || error != null ||
                playback?.isEnded == true && !playback.localReplayEnabled,
        )
        // 失败提示位于手势层之上，重试按钮不会被背景播放手势截获。
        if (success?.error != null && error == null) {
            Surface(modifier = Modifier.align(Alignment.Center).padding(24.dp)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(success.error)
                    TextButton(onClick = viewModel::loadPlayback) { Text("重试") }
                }
            }
        }
    }
}
