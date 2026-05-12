package tv.hsrui.bolo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object BoloShapes {
    object List {
        val Top = RoundedCornerShape(
            topStart = 16.dp, topEnd = 16.dp,
            bottomStart = 4.dp, bottomEnd = 4.dp
        )

        val Item = RoundedCornerShape(4.dp)

        val Bottom = RoundedCornerShape(
            topStart = 4.dp, topEnd = 4.dp,
            bottomStart = 16.dp, bottomEnd = 16.dp
        )
    }

    object InfoCard {
        val Default = RoundedCornerShape(12.dp)
        val Compact = RoundedCornerShape(4.dp)
    }
}