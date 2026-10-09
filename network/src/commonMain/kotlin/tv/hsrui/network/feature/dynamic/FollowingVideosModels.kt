package tv.hsrui.network.feature.dynamic

import tv.hsrui.network.model.Owner
import tv.hsrui.network.model.ValidVideosData
import tv.hsrui.network.model.VideoCard
import tv.hsrui.network.model.VideoCard.Stat
import tv.hsrui.network.model.VideosResult
import tv.hsrui.network.utils.toCountIntOrNull

data class FollowingVideosResponse(val raw: DynamicRawResponse) : VideosResult {
    override val isSuccess: Boolean get() = (raw.code == 0)
    override val message: String get() = raw.message
    override val validData: ValidVideosData = raw.data.items
        .filter { it.typeString == DynamicType.Video.typeString }
        .map { it.toVideoCard() }
        .let { list ->
            ValidVideosData(
                videosList = list,
                canLoadMore = raw.data.canLoadMore
            )
        }
    val offset get() = raw.data.offset
    val updateBaseline: String get() = raw.data.updateBaseline
    val isLoginExpired: Boolean get() = raw.code == -101
}

fun DynamicRawResponse.DynamicRawItem.toVideoCard(): VideoCard =
    VideoCard(
        avid = main.archive.avid,
        bvid = main.archive.bvid,
        title = main.archive.title,
        _cover = main.archive.coverUrl,
        _stat = Stat(
            view = main.archive.state.viewCountString.toCountIntOrNull() ?: 0,
            like = state.like.count,
            danmaku = main.archive.state.danmakuCountString.toCountIntOrNull() ?: 0,
            reply = state.comment.count
        ),
        _owner = Owner(
            mid = upInfo.mid,
            name = upInfo.name,
            face = upInfo.face
        ),
        _publishDateString = upInfo.pubDateString,
        _durationString = main.archive.durationString
    )
