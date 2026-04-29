package tv.hsrui.bolo

import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import tv.hsrui.bolo.main.MainScreen
import tv.hsrui.bolo.ui.theme.AppTheme

@Composable
@Preview
fun App() {
    AppTheme {
        val layoutDirection = LocalLayoutDirection.current
        Scaffold{innerPadding ->
            MainScreen(modifier = Modifier.padding(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection))
            )
        }
    }
}