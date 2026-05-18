package tv.hsrui.bolo.ui.components.error

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import multibili.composeapp.generated.resources.ErrorPig
import multibili.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.painterResource
import tv.hsrui.bolo.utils.setText

@Composable
fun ShowErrorContent(
    message: String = "",
    retry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var copyButtonText by remember { mutableStateOf("复制错误信息") }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(Res.drawable.ErrorPig),
                contentDescription = "错误",
                modifier = Modifier.widthIn(max = 192.dp).aspectRatio(1F)
            )
            if (message.isNotEmpty()) {
                Text(
                    text = message,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp).widthIn(max = 512.dp)
                )
                Button(onClick = {
                    scope.launch {
                        clipboard.setText(message)
                        copyButtonText = "错误信息已复制"
                    }
                }) {
                    Text(copyButtonText)
                }
            }
            if (retry != null) {
                Button(onClick = retry) {
                    Text("重试")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorPreview() {
    ShowErrorContent(
        message = "账号未登录",
        retry = {}
    )
}