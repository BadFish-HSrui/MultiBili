package tv.hsrui.network.constant

object LiveApiUrls {
    const val BASE = "https://api.live.bilibili.com/" //B站直播API地址
    const val RECOMMEND = "xlive/web-interface/v1/second/getUserRecommend" //直播推荐
    const val FOLLOWING = "xlive/web-ucenter/user/following" //关注直播
    const val AREA_LIST = "xlive/web-interface/v1/index/getWebAreaList" //直播分区目录
    const val SUB_AREA_LIST = "room/v1/area/getList" //主分区下的子分区目录
    const val AREA_ROOMS = "xlive/web-interface/v1/second/getList" //分区直播间列表
    const val ROOM_INFO = "xlive/web-room/v1/index/getInfoByRoom" //直播间与主播信息
    const val PLAY_INFO = "xlive/web-room/v2/index/getRoomPlayInfo" //直播播放信息与可选画质
    const val MESSAGE_INFO = "xlive/web-room/v1/index/getDanmuInfo" //直播信息流节点与鉴权
    const val DANMAKU_HISTORY = "xlive/web-room/v1/dM/gethistory" //直播间最近历史弹幕
    const val DANMAKU_SEND_CONFIG = "xlive/web-room/v1/index/getInfoByUser" //当前用户的直播弹幕发送配置
    const val DANMAKU_SEND = "msg/send" //发送直播弹幕
    const val AREA_PAGE = "https://live.bilibili.com/p/eden/area-tags" //直播分区网页信息
}
