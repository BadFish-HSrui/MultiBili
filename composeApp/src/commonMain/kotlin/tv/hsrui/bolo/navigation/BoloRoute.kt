package tv.hsrui.bolo.navigation

import kotlinx.serialization.Serializable
import tv.hsrui.bolo.model.Vid
import tv.hsrui.bolo.view.video.VideoPlaybackRequest

@Serializable
sealed interface BoloRoute {
    @Serializable data object Debug : BoloRoute

    @Serializable data object Main : BoloRoute

    @Serializable
    sealed interface User : BoloRoute {
        @Serializable data class Space(val mid: Long) : User
        @Serializable data class UploadsSearch(val mid: Long) : User
        @Serializable data class CollectionSearch(val mid: Long, val seasonId: Long) : User
        @Serializable data class Collection(val mid: Long, val seasonId: Long) : User
        @Serializable data class Series(val mid: Long, val seriesId: Long) : User
    }

    @Serializable
    sealed interface Favorite : BoloRoute {
        @Serializable data object List : Favorite
        @Serializable data class Folder(val mediaId: Long) : Favorite
        @Serializable data class Search(val mediaId: Long) : Favorite
    }

    @Serializable
    sealed interface Search : BoloRoute {
        @Serializable data object Input : Search
        @Serializable data class Results(val keyword: String) : Search
    }

    @Serializable
    sealed interface Login : BoloRoute {
        @Serializable data object Screen : Login
        @Serializable data object Webview : Login
    }

    @Serializable
    sealed interface AccountFeature : BoloRoute {
        @Serializable data object List : AccountFeature
        @Serializable data object History : AccountFeature
        @Serializable data object HistorySearch : AccountFeature
        @Serializable data object WatchLater : AccountFeature
        @Serializable data object WatchLaterSearch : AccountFeature
        @Serializable data object Download : AccountFeature
    }

    @Serializable
    sealed interface BoloSetting : BoloRoute {
        @Serializable data object List : BoloSetting
        @Serializable data object General : BoloSetting
        @Serializable data object Playback : BoloSetting
        @Serializable data object About : BoloSetting
    }

    @Serializable
    sealed interface View : BoloRoute {
        @Serializable data class Video(val vid: Vid) : View
        @Serializable data class VideoList(val request: VideoPlaybackRequest.VideoList) : View
        @Serializable data class Media(val seasonId: Long = 0, val episodeId: Long = 0) : View
    }
}
