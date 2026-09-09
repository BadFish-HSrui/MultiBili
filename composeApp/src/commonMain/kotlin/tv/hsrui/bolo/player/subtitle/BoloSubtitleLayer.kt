package tv.hsrui.bolo.player.subtitle

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BoloSubtitleLayer(
    controller: BoloSubtitleController,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        if (state.text.isNotEmpty()) {
            Text(
                text = state.text,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 64.dp),
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 20.sp,
                    shadow = Shadow(Color.Black, Offset(1f, 1f), 4f),
                ),
            )
        }
    }
}
