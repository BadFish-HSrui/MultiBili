/**
 * 此处仅供本地调试使用，在本文件编写或引用调试代码
 * 将引用的调试代码放置在 [tv.hsrui.bolo.debug.debugContent] 包，此包被git忽略
 * 不要提交对此文件的更改
 */

package tv.hsrui.bolo.debug

import androidx.compose.runtime.Composable
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator

@Composable
fun DebugScreen() {
    val navigator: Navigator = koinInject()
    navigator.navigateTo(BoloRoute.Main)
}