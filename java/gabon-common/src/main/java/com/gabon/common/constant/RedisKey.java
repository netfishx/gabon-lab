package com.gabon.common.constant;

 /**


 **/

public class RedisKey {

    /**
     * 验证码缓存key，第一个是类型,第二个是唯一标识比如手机号或者邮箱
     */
    public static final String CHECK_CODE_KEY = "code:%s:%s";


    /**
     * 提交订单令牌的缓存key
     */
    public static final String SUBMIT_ORDER_TOKEN_KEY = "order:submit:%s:%s";

    /**
     * 视频点击播放去重key: play:dedup:click:{videoId}:{ip}
     * TTL 24h，同一 IP 对同一视频 24h 内只计一次
     */
    public static final String PLAY_DEDUP_CLICK = "play:dedup:click:%d:%s";

    /**
     * 视频有效播放去重key: play:dedup:valid:{videoId}:{ip}
     * TTL 24h，同一 IP 对同一视频 24h 内只计一次
     */
    public static final String PLAY_DEDUP_VALID = "play:dedup:valid:%d:%s";

    /**
     * 日报上传者点击计数key: play:report:{date}:uploader:{uploaderId}:clicks
     * date 格式: yyyy-MM-dd（北京时间），TTL 48h
     */
    public static final String PLAY_REPORT_UPLOADER_CLICKS = "play:report:%s:uploader:%d:clicks";

    /**
     * 日报上传者有效播放计数key: play:report:{date}:uploader:{uploaderId}:valid
     * date 格式: yyyy-MM-dd（北京时间），TTL 48h
     */
    public static final String PLAY_REPORT_UPLOADER_VALID = "play:report:%s:uploader:%d:valid";

    /**
     * 当日有播放记录的上传者ID集合: play:report:{date}:uploaders
     * date 格式: yyyy-MM-dd（北京时间），TTL 48h
     */
    public static final String PLAY_REPORT_UPLOADERS = "play:report:%s:uploaders";

    /**
     * 视频点赞去重key: like:video:{videoId}:{userId}
     * TTL 7天，过期后视为未点赞
     */
    public static final String LIKE_VIDEO_USER = "like:video:%d:%d";

    /**
     * 当日有广告播放记录的广告商ID集合: ad:report:{date}:advertisers
     * date 格式: yyyy-MM-dd（北京时间），TTL 48h
     */
    public static final String AD_REPORT_ADVERTISERS = "ad:report:%s:advertisers";

    /**
     * 当日广告商播放次数: ad:report:{date}:advertiser:{advertiserId}:plays
     * date 格式: yyyy-MM-dd（北京时间），TTL 48h
     */
    public static final String AD_REPORT_ADVERTISER_PLAYS = "ad:report:%s:advertiser:%d:plays";

}
