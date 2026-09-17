package tv.hsrui.network.feature.video

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.model.Owner
import tv.hsrui.network.feature.video.collection.VideoCollectionData
import tv.hsrui.network.utils.formatToDateTime
import tv.hsrui.network.utils.formatToDuration
import tv.hsrui.network.utils.toHttpsUrl

enum class CopyrightType(val title: String) {
    Original("原创"),
    Reprint("转载"),
    Other("未填写")
}

@Serializable
data class VideoInfoResponse(
    @SerialName("code") private val _code: Int = -1,
    @SerialName("message") private val _message: String = "",
    val data: VideoInfoData = VideoInfoData()
) {
    private fun Int.toStateString(): String = when (this) {
        -1 -> "视频待审核"
        -2 -> "视频未通过审核"
        -3 -> "视频被网警锁定"
        -4 -> "视频被锁定(撞车?)"
        -5 -> "视频被管理员锁定"
        -6 -> "视频待审核(修复)"
        -7 -> "视频暂缓审核"
        -8 -> "视频待审核(补档)"
        -9 -> "视频正在等待转码"
        -10 -> "视频待审核(延迟)"
        -11 -> "视频源待修复"
        -12 -> "视频转储失败"
        -13 -> "视频待审核(允许评论)"
        -14 -> "视频在临时回收站中"
        -15 -> "视频分发中"
        -16 -> "视频转码失败"
        -20 -> "视频已创建(未提交)"
        -30 -> "视频已创建(未提交)"
        -40 -> "视频等待定时发布"
        -50 -> "视频仅UP主可见"
        -100 -> "视频已被UP主自行删除"
        else -> "视频状态正常，看到这条说明我写出BUG了:("
    }

    val code by lazy { if (_code == 0 && data.stateCode < 0) data.stateCode else _code }
    val message by lazy { if (_code == 0 && data.stateCode < 0) data.stateCode.toStateString() else _message }
    val isSuccess get() = (code == 0)
}

@Serializable
data class VideoInfoData(
    @SerialName("aid") val avid: Long = 0,
    @SerialName("bvid") val bvid: String = "",
    @SerialName("cid") val cid: Long = 0,
    @SerialName("videos") val videosCount: Int = 0,
    @SerialName("copyright") private val _copyright: Int = 0,
    @SerialName("pic") private val _pic: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("pubdate") val publishDate: Long = 0,
    @SerialName("desc") val description: String = "",
    @SerialName("state") val stateCode: Int = -114514,
    @SerialName("rights") val rights: Rights = Rights(),
    @SerialName("owner") private val _owner: Owner = Owner(),
    @SerialName("stat") val stateCount: Stat = Stat(),
    @SerialName("argue_info") private val _argueInfo: ArgueInfo = ArgueInfo(),
    @SerialName("dynamic") val dynamicDescription: String = "",
    @SerialName("dimension") val dimension: Dimension = Dimension(),
    @SerialName("is_upower_exclusive") val isUpowerExclusive: Boolean = false, //充电专属视频
    @SerialName("is_season_display") val isSeasonDisplay: Boolean = false,
    @SerialName("ugc_season") val collection: VideoCollectionData? = null,
    @SerialName("pages") private val rawParts: List<VideoPartData> = emptyList(),
) {
    val parts by lazy { rawParts.filter { it.cid > 0 }.distinctBy { it.cid }.sortedBy { it.pageNumber } }

    val copyrightType: CopyrightType
        get() = when (_copyright) {
            1 -> CopyrightType.Original
            2 -> CopyrightType.Reprint
            3 -> CopyrightType.Other
            else -> CopyrightType.Other
        }
    val coverUrl by lazy { _pic.toHttpsUrl() }
    val publishDateString by lazy { publishDate.formatToDateTime() }

    val isCooperation: Boolean by lazy { (rights.isCooperation == 1) }

    val upMid by _owner::mid
    val upName by _owner::name
    val upAvatarUrl by lazy { _owner.face.toHttpsUrl() }

    val argueMessage: String by _argueInfo::message

    @Serializable
    data class Rights(
        @SerialName("is_cooperation") val isCooperation: Int = 0
    )

    @Serializable
    data class Stat(
        val view: Int = -1,
        val danmaku: Int = -1,
        val reply: Int = -1,
        val favorite: Int = -1,
        val coin: Int = -1,
        val share: Int = -1,
        val like: Int = -1,
    )

    @Serializable
    data class ArgueInfo(
        @SerialName("argue_msg") val message: String = ""
    )
}

@Serializable
data class VideoPartData(
    @SerialName("cid") val cid: Long = 0,
    @SerialName("page") val pageNumber: Int = 0,
    @SerialName("part") val title: String = "",
    @SerialName("duration") val duration: Int = 0,
) {
    val durationString get() = if (duration > 0) duration.formatToDuration() else ""
}

@Serializable
data class Dimension(
    val width: Int = 0,
    val height: Int = 0
)

val VideoInfoResponseExample = VideoInfoResponse(
    _code = 0,
    _message = "OK",
    data = VideoInfoData(
        avid = 115327790751441,
        bvid = "BV1gDxEzHE8Z",
        cid = 32882624272,
        videosCount = 1,
        _copyright = 1,
        _pic = "http://i2.hdslb.com/bfs/archive/7a7aa5e03fb63167e51a9d3d7a28ed2749128a45.jpg",
        title = "✨“我为你唱一曲如游丝的气息”《青衣DJ》✨/AI東 雪蓮",
        publishDate = 1759762729,
        description = "原曲：青衣DJ\n" +
                "人声：AI东雪莲\n" +
                "图/动态图/音频：\n" +
                "pan.quark.cn/s/5d94a5c96ba9\n" +
                "本身想跑花旦风格的，但是发现这个底模跑不出好看的\n" +
                "做了22张动图，没用上的图和动图放网盘里了\n" +
                "中秋快乐！\n" +
                "这几天感冒严重，打火机日语完整版过几天做完",
        stateCode = 0,
        rights = VideoInfoData.Rights(
            isCooperation = 0
        ),
        _owner = Owner(
            mid = 1060544882,
            name = "东洋雪莲",
            face = "https://i2.hdslb.com/bfs/face/4cbf2f66d23a324ecca8d3c07adbcafecfef829b.jpg"
        ),
        stateCount = VideoInfoData.Stat(
            view = 2053797,
            danmaku = 864,
            reply = 3532,
            favorite = 77387,
            coin = 13210,
            share = 4613,
            like = 162535
        ),
        _argueInfo = VideoInfoData.ArgueInfo(message = "该内容疑似使用AI技术合成， 请谨慎甄别"),
        dynamicDescription = "",
        dimension = Dimension(width = 1920, height = 1080),
        isUpowerExclusive = false,
        isSeasonDisplay = true
    )
)
