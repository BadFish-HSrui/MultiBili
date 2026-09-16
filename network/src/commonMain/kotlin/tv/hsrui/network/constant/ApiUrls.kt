package tv.hsrui.network.constant

object ApiUrls {
    const val BASE = "https://api.bilibili.com/"  //B站API地址
    const val POPULAR = "x/web-interface/popular"  //热门视频
    const val RECOMMEND = "x/web-interface/wbi/index/top/feed/rcmd" //首页推荐
    const val SEARCH = "x/web-interface/wbi/search/type" //分类搜索
    const val BUVID3 = "x/web-frontend/getbuvid" //匿名请求标识
    const val REGION = "x/web-interface/region/feed/rcmd" //分区
    const val MEDIA_INDEX = "pgc/season/index/result" //影视番剧索引
    const val MEDIA_CONDITIONS = "pgc/season/index/condition" //影视番剧筛选条件
    const val MEDIA_SEASON = "pgc/view/web/season" //媒体详情与分季
    const val MEDIA_RELATED = "pgc/season/web/related/recommend" //媒体推荐
    const val ACCOUNT_INFO = "x/space/v2/info" //个人空间信息
    const val MY_ACCOUNT_INFO = "x/space/v2/myinfo" //我的个人空间信息
    const val FOLLOW_STATE = "x/relation/stat" //关注与被关注
    const val USER_INFO = "x/space/wbi/acc/info" //其他用户资料
    const val USER_UP_STAT = "x/space/upstat" //用户获赞与视频播放统计
    const val HISTORY = "x/web-interface/history/cursor" //历史记录
    const val HISTORY_DELETE = "x/v2/history/delete" //删除历史记录
    const val DYNAMIC = "x/polymer/web-dynamic/v1/feed/all" //动态
    const val VIDEO_INFO = "x/web-interface/wbi/view" //视频播放信息
    const val VIDEO_RELATED = "x/web-interface/archive/related" //相关视频推荐
    const val RELATION = "x/web-interface/relation" //关系
    const val MODIFY_RELATION = "x/relation/modify" //修改关系

    object UserSpace {
        const val PRIVACY = "https://space.bilibili.com/ajax/settings/getSettings"
        const val UPLOADS = "x/space/wbi/arc/search"
        const val COLLECTIONS = "x/polymer/web-space/seasons_series_list"
        const val LIKES = "x/space/like/video"
        const val COINS = "x/space/coin/video"
    }

    object VideoAction {
        const val LIKE = "x/web-interface/archive/like" //点赞
        const val HAS_LIKE = "x/web-interface/archive/has/like" //点赞状态
        const val COIN = "x/web-interface/coin/add" // 投币
        const val HAS_COIN = "x/web-interface/archive/coins" //投币状态
        const val HAS_FAVOURED = "x/v2/fav/video/favoured" //收藏状态
        const val TRIPLE = "x/web-interface/archive/like/triple" //一键三连
    }

    object WatchLater {
        const val LIST = "x/v2/history/toview" //稍后再看列表
        const val ADD = "x/v2/history/toview/add" //添加稍后再看
        const val DELETE = "x/v2/history/toview/del" //移除稍后再看
        const val DELETE_ALL = "x/v2/history/toview/clear" //清空稍后再看
    }

    object Favorite {
        const val CREATED_FOLDERS = "x/v3/fav/folder/created/list-all" //创建的收藏夹列表
        const val CREATE_FOLDER = "x/v3/fav/folder/add" //创建收藏夹
        const val DELETE_FOLDER = "x/v3/fav/folder/del" //删除收藏夹
        const val FOLDER_INFO = "x/v3/fav/folder/info" //收藏夹元数据
        const val FOLDER_CONTENT = "x/v3/fav/resource/list" //收藏夹内容
        const val MODIFY_RESOURCE = "x/v3/fav/resource/deal" //修改视频收藏夹归属
        const val REMOVE_RESOURCE = "x/v3/fav/resource/batch-del" //移除收藏夹内容
    }

    object Reply {
        const val LIST = "x/v2/reply/wbi/main" //评论列表
        const val SUB_LIST = "x/v2/reply/reply" //子评论列表
        const val LIKE = "x/v2/reply/action" //点赞
        const val DISLIKE = "x/v2/reply/hate" //点踩
        const val SEND = "x/v2/reply/add" //发送评论
    }

    object Play {
        const val REPORT_START = "x/click-interface/click/web/h5" //开始播放上报
        const val REPORT_PROGRESS = "x/v2/history/report" //播放进度上报
        const val VIDEO = "x/player/wbi/playurl" //视频播放信息
        const val MEDIA = "pgc/player/web/playurl" //媒体播放信息
    }

    object Danmaku {
        const val SEGMENT = "x/v2/dm/wbi/web/seg.so" //分段弹幕
    }

    object Subtitle {
        const val LIST = "x/player/wbi/v2" //字幕列表
    }

    const val WBI = "x/web-interface/nav" //用于获取WbiKey，实际上是导航栏接口
}
