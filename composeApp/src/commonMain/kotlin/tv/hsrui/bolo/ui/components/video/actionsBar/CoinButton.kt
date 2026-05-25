package tv.hsrui.bolo.ui.components.video.actionsBar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import multibili.composeapp.generated.resources.Res
import multibili.composeapp.generated.resources.coin_icon
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.components.dialog.ShowConfirmDialog
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.network.feature.video.CopyrightType
import tv.hsrui.network.feature.video.VideoInfoData
import tv.hsrui.network.feature.video.actions.coin.modifyVideoCoin
import tv.hsrui.network.utils.formatCountToString

@Composable
fun CoinButton(
    videoInfo: VideoInfoData,
    isCoined: Boolean,
    coinedCount: Int,
    canClick: Boolean,
    reloadState: suspend () -> Unit,
    modifier: Modifier = Modifier
) {
    val canCoinCount =
        (if (videoInfo.copyrightType == CopyrightType.Reprint) 1 else 2) - coinedCount
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarManager: SnackbarManager = koinInject()
    val scope = rememberCoroutineScope()

    Box(modifier = modifier) {
        Surface(
            onClick = {
                if (canClick && canCoinCount > 0) {
                    showDialog = true
                }
            },
            color = Color.Transparent
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(Res.drawable.coin_icon),
                    contentDescription = "点赞",
                    tint = if (isCoined) BiliColor.ThemeColor else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = videoInfo.stateCount.coin.formatCountToString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    if (showDialog) {
        var coinCount by rememberSaveable { mutableStateOf(1) }
        ShowConfirmDialog(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("选择投币数量")

                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = (coinCount == 1),
                            onClick = { coinCount = 1 },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = 0,
                                count = canCoinCount
                            ),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.coin_icon),
                                contentDescription = "投一个币",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        if (canCoinCount > 1) {
                            SegmentedButton(
                                selected = (coinCount == 2),
                                onClick = { coinCount = 2 },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = 1,
                                    count = canCoinCount
                                )
                            ) {
                                Row {
                                    Icon(
                                        painter = painterResource(Res.drawable.coin_icon),
                                        contentDescription = "投两个币",
                                        modifier = Modifier.size(24.dp).offset(x = (+4).dp)
                                    )
                                    Icon(
                                        painter = painterResource(Res.drawable.coin_icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp).offset(x = (-4).dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            onCancel = { showDialog = false },
            onConfirm = {
                scope.launch {
                    try {
                        val result = modifyVideoCoin(avid = videoInfo.avid, coinCount = coinCount)
                        if (result.isSuccess) {
                            snackbarManager.showMessage("成功投币${coinCount}枚")
                        } else {
                            snackbarManager.showMessage("[${result.code}]: ${result.message}")
                        }
                    } catch (e: Exception) {
                        snackbarManager.showMessage(e.message ?: "其他网络错误")
                    } finally {
                        reloadState()
                        showDialog = false
                    }
                }
            }
        )
    }
}