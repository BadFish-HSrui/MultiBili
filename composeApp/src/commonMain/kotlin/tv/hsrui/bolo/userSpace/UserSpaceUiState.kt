package tv.hsrui.bolo.userSpace

import tv.hsrui.network.feature.favorite.FavoriteFolderInfoData
import tv.hsrui.network.model.VideoCard

sealed interface UserSpaceSectionState<out T> {
    data object Loading : UserSpaceSectionState<Nothing>
    data object Hidden : UserSpaceSectionState<Nothing>
    data class Success<T>(val data: T) : UserSpaceSectionState<T>
    data class Error(val message: String) : UserSpaceSectionState<Nothing>

    val isVisible: Boolean
        get() = when (this) {
            Hidden -> false
            is Success -> when (data) {
                is Collection<*> -> data.isNotEmpty()
                is Boolean -> data
                else -> true
            }
            else -> true
        }
}

data class UserSpaceUiState(
    val uploads: UserSpaceSectionState<List<VideoCard>> = UserSpaceSectionState.Loading,
    val collections: UserSpaceSectionState<Boolean> = UserSpaceSectionState.Loading,
    val likes: UserSpaceSectionState<List<VideoCard>> = UserSpaceSectionState.Loading,
    val coins: UserSpaceSectionState<List<VideoCard>> = UserSpaceSectionState.Loading,
    val favorites: UserSpaceSectionState<List<FavoriteFolderInfoData>> = UserSpaceSectionState.Loading,
    val refreshGeneration: Int = 0,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val uploadPage: Int = 0,
    val loadMoreError: String? = null,
) {
    val visibleTabs get() = UserSpaceTab.entries.filter {
        when (it) {
            UserSpaceTab.Uploads -> uploads.isVisible
            UserSpaceTab.Collections -> collections.isVisible
            UserSpaceTab.Likes -> likes.isVisible
            UserSpaceTab.Coins -> coins.isVisible
            UserSpaceTab.Favorites -> favorites.isVisible
        }
    }
}
