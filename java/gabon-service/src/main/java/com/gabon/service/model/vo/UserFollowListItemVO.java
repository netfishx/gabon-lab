package com.gabon.service.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gabon.service.config.InstantToSecondsSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

@Data
@Schema(description = "关注/粉丝列表项")
public class UserFollowListItemVO {
    @Schema(description = "用户ID", example = "2")
    private Long id;
    
    @Schema(description = "用户昵称", example = "张三")
    private String name;
    
    @Schema(description = "头像URL（没有头像返回null）", example = "https://aitools888.s3.ap-east-1.amazonaws.com/images/2/abc123.jpg")
    private String avatarUrl;
    
    @Schema(description = "VIP等级: 0=非VIP, 1=VIP", example = "1")
    private Integer isVip;
    
    @Schema(description = "个性签名（没有签名返回null）", example = "分享生活美好瞬间")
    private String signature;
    
    @Schema(type = "integer", description = "关注时间（时间戳秒）", example = "1739174903")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant followTime;
    
    @Schema(description = "关注状态: 0=未关注, 1=已关注, 2=相互关注 | Follow status: 0=Not Following, 1=Following, 2=Mutual Following", example = "2")
    private Integer followStatus;
}
