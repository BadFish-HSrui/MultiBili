package tv.hsrui.bolo.ui.common.reply

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.reply.ReplyItem

private const val EnterAnimationDurationMillis = 300
private const val ExitAnimationDurationMillis = 220
private const val PredictiveBackTranslationFactor = 0.75f
private const val PredictiveBackAlphaFactor = 0.3f

@Stable
internal class SubRepliesSurfaceState : ViewModel() {
    private val animatedTranslationFraction = Animatable(1f)
    private val animatedAlpha = Animatable(1f)

    var currentReply by mutableStateOf<ReplyItem?>(null)
        private set

    val isVisible: Boolean
        get() = currentReply != null

    val translationFraction: Float
        get() = animatedTranslationFraction.value

    val alpha: Float
        get() = animatedAlpha.value

    suspend fun show(reply: ReplyItem) {
        animatedTranslationFraction.snapTo(1f)
        animatedAlpha.snapTo(1f)
        currentReply = reply
        animatedTranslationFraction.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = EnterAnimationDurationMillis,
                easing = FastOutSlowInEasing
            )
        )
    }

    suspend fun dismiss() {
        if (!isVisible) return

        animatedTranslationFraction.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = ExitAnimationDurationMillis,
                easing = FastOutLinearInEasing
            )
        )
        currentReply = null
        animatedAlpha.snapTo(1f)
    }

    suspend fun dismissFromPredictiveBack(progress: Float) {
        if (!isVisible) return

        val normalizedProgress = progress.coerceIn(0f, 1f)
        animatedTranslationFraction.snapTo(
            normalizedProgress * PredictiveBackTranslationFactor
        )
        animatedAlpha.snapTo(1f - normalizedProgress * PredictiveBackAlphaFactor)
        animatedTranslationFraction.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = ExitAnimationDurationMillis,
                easing = FastOutLinearInEasing
            )
        )
        currentReply = null
        animatedAlpha.snapTo(1f)
    }

    suspend fun cancelPredictiveBack(progress: Float) {
        if (!isVisible) return

        val normalizedProgress = progress.coerceIn(0f, 1f)
        animatedTranslationFraction.snapTo(
            normalizedProgress * PredictiveBackTranslationFactor
        )
        animatedAlpha.snapTo(1f - normalizedProgress * PredictiveBackAlphaFactor)
        coroutineScope {
            launch {
                animatedTranslationFraction.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = ExitAnimationDurationMillis,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            launch {
                animatedAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = ExitAnimationDurationMillis,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }
    }
}

@Composable
internal fun rememberSubRepliesSurfaceState(): SubRepliesSurfaceState =
    viewModel { SubRepliesSurfaceState() }

@Composable
internal fun SubRepliesSurface(
    state: SubRepliesSurfaceState,
    predictiveBackProgress: Float?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (ReplyItem) -> Unit
) {
    val currentReply = state.currentReply ?: return
    val normalizedBackProgress = predictiveBackProgress?.coerceIn(0f, 1f)
    val translationFraction = normalizedBackProgress?.times(PredictiveBackTranslationFactor)
        ?: state.translationFraction
    val surfaceAlpha = normalizedBackProgress?.let {
        1f - it * PredictiveBackAlphaFactor
    } ?: state.alpha

    Box(
        modifier = modifier
            .fillMaxSize()
            // 固定命中整个信息区，动画露出的区域也不向下层 Pager 透传事件。
            .pointerInput(Unit) {}
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = size.height * translationFraction
                    alpha = surfaceAlpha
                },
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "相关评论",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭相关评论"
                        )
                    }
                }

                HorizontalDivider()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    content(currentReply)
                }
            }
        }
    }
}
