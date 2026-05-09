package tv.hsrui.network.feature.account.myinfo

import org.koin.dsl.module

val MyAccountInfoModule = module {
    single { MyAccountInfoManager() }
}