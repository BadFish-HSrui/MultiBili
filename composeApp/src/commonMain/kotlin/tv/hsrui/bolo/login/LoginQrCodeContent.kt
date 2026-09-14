package tv.hsrui.bolo.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.network.login.qrcode.LoginQrCodeStatus

@Composable
fun LoginQrCodeContent(
    state: QrCodeLoginUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("扫码登录", style = MaterialTheme.typography.titleLarge)
        when (state) {
            QrCodeLoginUiState.Idle, QrCodeLoginUiState.Loading -> {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is QrCodeLoginUiState.Error -> ShowErrorContent(
                message = state.message,
                retry = onRefresh,
                modifier = Modifier.fillMaxWidth().height(360.dp),
            )
            is QrCodeLoginUiState.Success -> {
                Card(
                    modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(1F),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (state.status == LoginQrCodeStatus.Expired) {
                            Text("二维码已失效", color = Color.Black)
                        } else {
                            Image(
                                painter = rememberQrCodePainter(state.url),
                                contentDescription = "哔哩哔哩登录二维码",
                                // 即使是最小的 21×21 二维码，四周也保留至少四个模块的白边。
                                modifier = Modifier.fillMaxSize(0.7F),
                            )
                        }
                    }
                }
                Text(
                    text = when (state.status) {
                        LoginQrCodeStatus.WaitingForScan -> "使用官方客户端扫码登录"
                        LoginQrCodeStatus.WaitingForConfirmation -> "已扫码，请在手机上确认登录"
                        LoginQrCodeStatus.Expired -> "请刷新二维码后重试"
                        LoginQrCodeStatus.Completed -> "登录成功"
                    },
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onRefresh) { Text("刷新二维码") }
            }
        }
    }
}
