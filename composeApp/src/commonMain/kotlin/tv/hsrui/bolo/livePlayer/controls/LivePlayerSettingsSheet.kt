package tv.hsrui.bolo.livePlayer.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scrim
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.boloSetting.setting.live.LiveDanmakuSettings
import tv.hsrui.bolo.boloSetting.setting.live.LiveDanmakuSettingsContent
import tv.hsrui.bolo.getPlatform

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LivePlayerSettingsSheet(
    isOpen: Boolean,
    settings: LiveDanmakuSettings,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val direction = LocalLayoutDirection.current
    val platform = getPlatform()
    val isIpad = platform.type == PlatformType.Ios && !platform.isPhone
    val scrimAlpha by animateFloatAsState(if (isOpen) 1f else 0f)
    LaunchedEffect(isOpen) { if (isOpen) drawer.open() else drawer.close() }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = isOpen || !drawer.isClosed,
        onBackCompleted = onDismissRequest,
    )
    if (!isOpen && drawer.isClosed && !drawer.isAnimationRunning) return
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val sheetWidth = min(300.dp, maxWidth * .85f)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ModalNavigationDrawer(
                drawerState = drawer,
                gesturesEnabled = false,
                scrimColor = Color.Unspecified,
                drawerContent = {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        ModalDrawerSheet(
                            modifier = Modifier.width(sheetWidth).fillMaxHeight(),
                            drawerShape = AbsoluteRoundedCornerShape(topLeft = 16.dp, bottomLeft = 16.dp),
                            windowInsets = if (isIpad) WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Right)
                                else WindowInsets(),
                        ) {
                            PrimaryTabRow(selectedTabIndex = pager.currentPage) {
                                listOf("播放器", "弹幕设置").forEachIndexed { index, title ->
                                    Tab(
                                        selected = pager.currentPage == index,
                                        enabled = isOpen,
                                        onClick = { scope.launch { pager.animateScrollToPage(index) } },
                                        text = { Text(title, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                                    )
                                }
                            }
                            HorizontalPager(
                                state = pager,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                userScrollEnabled = isOpen,
                                verticalAlignment = Alignment.Top,
                            ) { page ->
                                Column(
                                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                        .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (page == 1) LiveDanmakuSettingsContent(settings, isOpen && pager.currentPage == 1)
                                }
                            }
                        }
                    }
                },
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Scrim(contentDescription = "关闭播放器设置", onClick = onDismissRequest, alpha = { scrimAlpha })
                }
            }
        }
    }
}
