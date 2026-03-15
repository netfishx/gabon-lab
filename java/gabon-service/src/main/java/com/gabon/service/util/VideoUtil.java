package com.gabon.service.util;

/**
 * 视频相关工具类
 */
public class VideoUtil {
    
    /**
     * 将秒数格式化为 mm:ss 格式（用于视频时长显示）
     * @param seconds 秒数
     * @return 格式化后的时长字符串，如：180秒 -> "3:00"
     */
    public static String formatDuration(Integer seconds) {
        if (seconds == null || seconds < 0) {
            return "0:00";
        }
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }
}
