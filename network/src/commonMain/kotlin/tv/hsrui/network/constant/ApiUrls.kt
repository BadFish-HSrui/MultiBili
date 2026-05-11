package tv.hsrui.network.constant

object ApiUrls {
    const val BASE = "https://api.bilibili.com/"  //B站API地址
    const val POPULAR = "x/web-interface/popular"  //热门视频
    const val RECOMMEND =  "x/web-interface/wbi/index/top/feed/rcmd" //首页推荐
    const val REGION = "x/web-interface/region/feed/rcmd" //分区
    const val ACCOUNT_INFO = "x/space/v2/info" //个人空间信息
    const val MY_ACCOUNT_INFO = "x/space/v2/myinfo" //我的个人空间信息
    const val FOLLOW_STATE = "x/relation/stat" //关注与被关注
}