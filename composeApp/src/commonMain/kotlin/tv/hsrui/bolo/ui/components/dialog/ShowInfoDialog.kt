package tv.hsrui.bolo.ui.components.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tv.hsrui.bolo.ui.theme.BoloShapes
import kotlin.time.TimeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowInfoDialog(
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    forcedDisplaySeconds: Int? = null,
    content: @Composable () -> Unit,
) {
    var remainingSeconds by remember(forcedDisplaySeconds) {
        mutableIntStateOf(forcedDisplaySeconds?.coerceAtLeast(0) ?: 0)
    }
    LaunchedEffect(forcedDisplaySeconds) {
        val durationSeconds = forcedDisplaySeconds?.coerceAtLeast(0) ?: return@LaunchedEffect
        if (durationSeconds == 0) return@LaunchedEffect

        val startedAt = TimeSource.Monotonic.markNow()
        val durationMillis = durationSeconds.toLong() * 1_000L
        while (true) {
            val remainingMillis = (durationMillis - startedAt.elapsedNow().inWholeMilliseconds)
                .coerceAtLeast(0L)
            remainingSeconds = ((remainingMillis + 999L) / 1_000L).toInt()
            if (remainingSeconds == 0) break
            delay(remainingMillis - (remainingSeconds - 1L) * 1_000L)
        }
    }

    BasicAlertDialog(
        onDismissRequest = {
            if (forcedDisplaySeconds == null && confirmEnabled) onConfirm()
        },
    ) {
        Surface(
            modifier = Modifier.wrapContentSize(),
            shape = BoloShapes.InfoCard.Default,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 8.dp),
            ) {
                content()

                OutlinedButton(
                    onClick = onConfirm,
                    enabled = confirmEnabled && remainingSeconds == 0,
                ) {
                    Text(if (remainingSeconds > 0) "${remainingSeconds}s" else "确认")
                }
            }
        }
    }
}

@Composable
fun ShowInfoDialog(
    title: @Composable () -> Unit,
    onConfirm: () -> Unit,
    icon: @Composable (() -> Unit)? = null,
    text: String = "",
    confirmEnabled: Boolean = true,
    forcedDisplaySeconds: Int? = null,
    content: (@Composable () -> Unit)? = null,
) {
    ShowInfoDialog(
        onConfirm = onConfirm,
        confirmEnabled = confirmEnabled,
        forcedDisplaySeconds = forcedDisplaySeconds,
    ) {
        if (icon != null) icon()

        title()

        if (content != null) {
            content()
        } else if (text.isNotEmpty()) {
            Text(
                text = text,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp).alpha(0.8F),
            )
        }
    }
}
