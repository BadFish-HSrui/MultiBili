package tv.hsrui.bolo.storage.appData

import org.koin.core.qualifier.named
import org.koin.dsl.module
import tv.hsrui.bolo.storage.statistics.StatisticsDatabase
import tv.hsrui.bolo.storage.statistics.createStatisticsDatabase

val AppDataStorageModule = module {
    single { createStatisticsDatabase() }
    single { AppDataStorage(get(named("appData"))) { get<StatisticsDatabase>() } }
}
