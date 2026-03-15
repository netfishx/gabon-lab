package com.gabon.service.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 视频详情VO
 * 包含视频信息、作者信息、互动状态
 */
@Data
@Schema(description = "视频详情VO")
public class VideoDetailVO {

    @Schema(description = "视频信息")
    private VideoInfo video;

    @Schema(description = "作者信息")
    private AuthorInfo author;

    @Schema(description = "当前用户互动状态（如果未登录，全部为false）")
    private InteractionInfo interaction;

    /**
     * 视频信息
     */
    @Data
    @Schema(description = "视频信息")
    public static class VideoInfo {
        @Schema(description = "视频ID", example = "1")
        private Long id;

        @Schema(description = "视频文件URL", example = "https://s3.amazonaws.com/bucket/video.mp4")
        private String fileUrl;

        @Schema(description = "点赞数", example = "8920")
        private Long likeCount;

        @Schema(description = "视频标题", example = "时尚穿搭")
        private String title;

        @Schema(description = "视频描述", example = "时尚穿搭分享")
        private String description;

        @Schema(description = "标签列表", example = "[\"推荐\", \"至爱\", \"无需内容\"]")
        private List<String> tags;
    }

    /**
     * 作者信息
     */
    @Data
    @Schema(description = "作者信息")
    public static class AuthorInfo {
        @Schema(description = "作者ID", example = "2")
        private Long id;

        @Schema(description = "作者姓名", example = "张三")
        private String name;

        @Schema(description = "作者头像URL", example = "https://example.com/avatar.jpg")
        private String avatarUrl;

        @Schema(description = "是否为VIP: 0=否, 1=是", example = "1")
        private Integer isVip;
    }

    /**
     * 互动状态信息
     */
    @Data
    @Schema(description = "互动状态信息")
    public static class InteractionInfo {
        @Schema(description = "是否点赞", example = "false")
        private Boolean isLiked;

        @Schema(description = "是否关注作者", example = "true")
        private Boolean isFollowing;
    }
}
