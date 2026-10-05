package tv.hsrui.bolo.download

import org.koin.dsl.module

val DownloadModule = module {
    single { DownloadManager.instance }
}
