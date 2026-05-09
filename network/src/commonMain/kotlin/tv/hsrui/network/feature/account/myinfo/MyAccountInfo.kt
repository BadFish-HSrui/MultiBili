package tv.hsrui.network.feature.account.myinfo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MyAccountInfoManager {
    private var _info = MutableStateFlow(MyAccountInfo())
    var info = _info.asStateFlow()

    suspend fun loadInfo() {
        if (!_info.value.isSuccess) {
            _info.value = fetchMyAccountInfo()
        }
    }
}