package com.gabon.service.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "他人主页信息")
public class UserProfileVO {
    @Schema(description = "用户ID", example = "4")
    private Long id;
    
    @Schema(description = "用户昵称", example = "时尚穿搭")
    private String name;
    
    @Schema(description = "头像URL（没有头像返回null）", example = "https://...")
    private String avatarUrl;
    
    @Schema(description = "个性签名（没有签名返回null）", example = "让穿搭成为艺术 👗")
    private String signature;
    
    @Schema(description = "VIP状态: 0=非VIP, 1=VIP", example = "1")
    private Integer isVip;
    
    @Schema(description = "关注数（该用户关注了多少人）", example = "128")
    private Long followingCount;
    
    @Schema(description = "粉丝数（有多少人关注该用户）", example = "67200")
    private Long followersCount;
    
    @Schema(description = "关注状态: 0=未关注, 1=已关注, 2=相互关注 | Follow status: 0=Not Following, 1=Following, 2=Mutual Following", example = "1")
    private Integer followStatus;
}
