package tv.hsrui.bolo.utils

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun PaddingValues.calculateWithoutBottom(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = this.calculateStartPadding(layoutDirection),
        top = this.calculateTopPadding(),
        end = this.calculateEndPadding(layoutDirection),
        bottom = 0.dp
    )
}
