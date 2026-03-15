package com.gabon.service.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * 用户关注关系实体
 * 映射到user_follow表
 */
@Data
@TableName("user_follow")
@Schema(description = "用户关注关系实体")
public class UserFollow implements Serializable {

    @Schema(description = "主键ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "关注者用户ID", example = "1")
    @TableField("follower_id")
    private Long followerId;

    @Schema(description = "被关注用户ID", example = "2")
    @TableField("followed_id")
    private Long followedId;

    @Schema(description = "关注状态: 0=取消关注, 1=关注", example = "1")
    private Integer status;

    @Schema(description = "关注时间")
    @TableField("follow_time")
    private Instant followTime;

    @Schema(description = "创建时间")
    @TableField("create_time")
    private Instant createTime;

    @Schema(description = "更新时间")
    @TableField("update_time")
    private Instant updateTime;
}
