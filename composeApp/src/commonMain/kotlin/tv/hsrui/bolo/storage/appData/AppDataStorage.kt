package tv.hsrui.bolo.storage.appData

import eu.anifantakis.lib.ksafe.KSafePlain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppDataStorage(appDataKSafe: KSafePlain) {
    val searchHistory = SearchHistory(appDataKSafe)
    val oneTimeWarnings = OneTimeWarnings(appDataKSafe)
}

class SearchHistory internal constructor(
    private val appDataKSafe: KSafePlain,
) {
    private var storedItems by appDataKSafe(emptyList<String>(), key = "search_history_list")
    private val _items = MutableStateFlow(storedItems)

    val items: StateFlow<List<String>> = _items.asStateFlow()

    fun add(keyword: String) {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isEmpty()) return

        updateItems(buildList {
            add(normalizedKeyword)
            addAll(
                items.value
                    .filterNot { it == normalizedKeyword }
                    .take(19)
            )
        })
    }

    fun remove(keyword: String) {
        updateItems(items.value.filterNot { it == keyword })
    }

    fun clear() {
        updateItems(emptyList())
    }

    private fun updateItems(items: List<String>) {
        storedItems = items
        _items.value = items
    }
}
