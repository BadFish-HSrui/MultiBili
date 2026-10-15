package tv.hsrui.bolo.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BoloPlayerEndedContent(
    onReplay: () -> Unit,
    onPreviousEpisode: (() -> Unit)?,
    onNextEpisode: (() -> Unit)?,
    episodeNavigationEnabled: Boolean,
    actionsVisible: Boolean,
    modifier: Modifier = Modifier,
    endedContent: (@Composable () -> Unit)? = null,
) {
    val buttonScale = if (endedContent != null) 2f / 3f else 1f
    val minimumButtonSize = if (endedContent != null) 32.dp else LocalMinimumInteractiveComponentSize.current
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        if (actionsVisible) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides minimumButtonSize) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp * buttonScale),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 两侧固定占位，缺少切集按钮时重播仍在按钮行中央。
                        Box(Modifier.size(48.dp * buttonScale)) {
                            if (onPreviousEpisode != null) {
                                IconButton(
                                    onClick = onPreviousEpisode,
                                    enabled = episodeNavigationEnabled,
                                    modifier = Modifier.size(48.dp * buttonScale),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipPrevious,
                                        contentDescription = "上一集",
                                        modifier = Modifier.size(48.dp * buttonScale),
                                        tint = Color.White.copy(alpha = if (episodeNavigationEnabled) 1f else 0.38f),
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onReplay,
                            modifier = Modifier.size(64.dp * buttonScale),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Replay,
                                contentDescription = "重播",
                                modifier = Modifier.size(48.dp * buttonScale),
                                tint = Color.White,
                            )
                        }
                        Box(Modifier.size(48.dp * buttonScale)) {
                            if (onNextEpisode != null) {
                                IconButton(
                                    onClick = onNextEpisode,
                                    enabled = episodeNavigationEnabled,
                                    modifier = Modifier.size(48.dp * buttonScale),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipNext,
                                        contentDescription = "下一集",
                                        modifier = Modifier.size(48.dp * buttonScale),
                                        tint = Color.White.copy(alpha = if (episodeNavigationEnabled) 1f else 0.38f),
                                    )
                                }
                            }
                        }
                    }
                }
                endedContent?.invoke()
            }
        }
    }
}
