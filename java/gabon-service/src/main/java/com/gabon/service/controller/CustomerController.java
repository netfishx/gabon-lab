package com.gabon.service.controller;

import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.exception.BizException;
import com.gabon.common.util.JsonData;
import com.gabon.service.config.AuthInterceptor;
import com.gabon.service.model.dto.PresignedUploadUrlRequest;
import com.gabon.service.model.dto.ProfileResponse;
import com.gabon.service.model.dto.UpdateProfileRequest;
import com.gabon.service.model.vo.PresignedUploadUrlVO;
import com.gabon.service.model.entity.Customer;
import com.gabon.service.model.vo.UserFollowListItemVO;
import com.gabon.service.model.vo.UserProfileVO;
import com.gabon.service.service.CustomerService;
import com.gabon.service.service.UserFollowService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gabon.service.model.vo.CustomerTransactionVO;
import com.gabon.service.model.vo.WalletVO;
import com.gabon.service.service.CustomerTransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/customer")
@Tag(name = "用户管理", description = "用户资料展示、编辑、关注等用户管理相关接口")
public class CustomerController {

    @Autowired
    CustomerService customerService;
    @Autowired
    UserFollowService userFollowService;
    @Autowired
    CustomerTransactionService customerTransactionService;

    /**
     * 获取用户资料
     */
    @Operation(
        summary = "获取用户资料",
        description = "获取当前登录用户的完整资料信息",
        security = @SecurityRequirement(name = "Bearer")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
    })
    @GetMapping("/profile")
    public JsonData<ProfileResponse> getProfile() {
        Customer customer = AuthInterceptor.threadLocal.get();
        if (customer == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }

        ProfileResponse response = customerService.getProfile(customer.getId());
        return JsonData.buildSuccess(response);
    }

    /**
     * 更新用户资料
     */
    @Operation(
        summary = "更新用户资料",
        description = "更新当前登录用户的资料信息，包括昵称、头像、个性签名、邮箱、手机号、取款密码等",
        security = @SecurityRequirement(name = "Bearer")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误（邮箱格式错误、密码长度不符合要求等）"),
        @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
    })
    @PutMapping("/profile")
    public JsonData<ProfileResponse> updateProfile(
        @Parameter(description = "更新资料请求信息 (必填) | Update profile request info (required)", required = true)
        @Valid @RequestBody UpdateProfileRequest request) {

        Customer customer = AuthInterceptor.threadLocal.get();
        if (customer == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }

        ProfileResponse response = customerService.updateProfile(request, customer.getId());
        return JsonData.buildSuccess(response);
    }

    /**
     * 获取头像上传预签名URL
     */
    @Operation(
        summary = "获取头像上传URL",
        description = "获取S3预签名URL，前端拿到后直接PUT图片到S3，完成后调用更新资料接口传入avatarUrl",
        security = @SecurityRequirement(name = "Bearer")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "400", description = "文件类型错误（仅支持图片）"),
        @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
    })
    @PostMapping("/avatar-upload-url")
    public JsonData<PresignedUploadUrlVO> getAvatarUploadUrl(
        @Valid @RequestBody PresignedUploadUrlRequest request) {

        Customer customer = AuthInterceptor.threadLocal.get();
        if (customer == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }

        PresignedUploadUrlVO vo = customerService.getAvatarUploadUrl(customer.getId(), request);
        return JsonData.buildSuccess(vo);
    }

    /**
     * 关注用户
     */
    @Operation(
        summary = "关注用户",
        description = "关注指定用户",
        security = @SecurityRequirement(name = "Bearer")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "关注成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误（不能关注自己、用户不存在等）"),
        @ApiResponse(responseCode = "401", description = "未认证或令牌无效"),
        @ApiResponse(responseCode = "409", description = "已经关注该用户")
    })
    @PostMapping("/{userId}/follow")
    public JsonData<Void> followUser(
        @Parameter(description = "被关注用户ID (必填) | User ID to follow (required)", required = true, example = "2")
        @PathVariable Long userId) {

        Customer currentUser = AuthInterceptor.threadLocal.get();
        if (currentUser == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }

        userFollowService.followUser(currentUser.getId(), userId);
        return JsonData.buildSuccess();
    }

    /**
     * 取消关注用户
     */
    @Operation(
        summary = "取消关注用户",
        description = "取消关注指定用户",
        security = @SecurityRequirement(name = "Bearer")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "取消关注成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误（不能取消关注自己、用户不存在等）"),
        @ApiResponse(responseCode = "401", description = "未认证或令牌无效"),
        @ApiResponse(responseCode = "404", description = "未关注该用户，无法取消关注")
    })
    @DeleteMapping("/{userId}/follow")
    public JsonData<Void> unfollowUser(
        @Parameter(description = "被取消关注用户ID (必填) | User ID to unfollow (required)", required = true, example = "2")
        @PathVariable Long userId) {

        Customer currentUser = AuthInterceptor.threadLocal.get();
        if (currentUser == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }

        userFollowService.unfollowUser(currentUser.getId(), userId);
        return JsonData.buildSuccess();
    }

    /**
     * 获取我关注的人列表
     */
    @Operation(
        summary = "获取我的关注列表",
        description = "获取当前登录用户关注的所有用户列表（不分页）",
        security = @SecurityRequirement(name = "Bearer")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
    })
    @GetMapping("/following")
    public JsonData<List<UserFollowListItemVO>> getFollowingList() {
        Customer customer = AuthInterceptor.threadLocal.get();
        if (customer == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }

        List<UserFollowListItemVO> result = userFollowService.getFollowingList(customer.getId());
        return JsonData.buildSuccess(result);
    }

    /**
     * 获取我的粉丝列表
     */
    @Operation(
        summary = "获取我的粉丝列表",
        description = "获取所有关注当前登录用户的用户列表",
        security = @SecurityRequirement(name = "Bearer")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
    })
    @GetMapping("/followers")
    public JsonData<List<UserFollowListItemVO>> getFollowersList() {
        Customer customer = AuthInterceptor.threadLocal.get();
        if (customer == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }

        List<UserFollowListItemVO> result = userFollowService.getFollowersList(customer.getId());
        return JsonData.buildSuccess(result);
    }

    /**
     * 获取他人主页信息
     */
    @Operation(
        summary = "获取他人主页信息",
        description = "获取指定用户的主页信息，包括基本信息、关注数、粉丝数、是否已关注等。公开接口，可选认证（提供token可获取是否已关注状态）。",
        security = @SecurityRequirement(name = "Bearer")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "404", description = "用户不存在或已被删除")
    })
    @GetMapping("/{userId}/profile")
    public JsonData<UserProfileVO> getUserProfile(
        @Parameter(description = "用户ID (必填) | User ID (required)", required = true, example = "4")
        @PathVariable Long userId) {
        
        // 可选认证：如果已登录，获取当前用户ID
        Customer currentUser = AuthInterceptor.threadLocal.get();
        Long currentUserId = currentUser != null ? currentUser.getId() : null;
        
        UserProfileVO response = customerService.getUserProfile(userId, currentUserId);
        return JsonData.buildSuccess(response);
    }

    /**
     * 获取他人的关注/粉丝列表
     */
    @Operation(
        summary = "获取他人的关注/粉丝列表",
        description = "获取指定用户的关注列表或粉丝列表。通过 type 参数区分：1=关注列表，2=粉丝列表。公开接口，可选认证（提供token可获取当前用户是否关注了列表中的用户）。",
        security = @SecurityRequirement(name = "Bearer")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "400", description = "参数错误（type 必须是 1 或 2）"),
        @ApiResponse(responseCode = "404", description = "用户不存在或已被删除")
    })
    @GetMapping("/{userId}/follow")
    public JsonData<List<UserFollowListItemVO>> getUserFollowList(
        @Parameter(description = "用户ID (必填) | User ID (required)", required = true, example = "4")
        @PathVariable Long userId,
        @Parameter(description = "列表类型 (必填): 1=关注列表, 2=粉丝列表 | List type (required): 1=following list, 2=followers list", required = true, example = "1")
        @RequestParam(value = "type", required = true) Integer type) {
        
        // 参数校验：type 必须是 1 或 2
        if (type == null || (type != 1 && type != 2)) {
            throw BizCodeEnum.PARAM_ERROR.format("type 参数必须是 1（关注列表）或 2（粉丝列表）");
        }
        
        // 可选认证：如果已登录，获取当前用户ID
        Customer currentUser = AuthInterceptor.threadLocal.get();
        Long currentUserId = currentUser != null ? currentUser.getId() : null;
        
        // 根据 type 参数调用不同的服务方法
        List<UserFollowListItemVO> result;
        if (type == 1) {
            result = userFollowService.getUserFollowingList(userId, currentUserId);
        } else {
            result = userFollowService.getUserFollowersList(userId, currentUserId);
        }
        
        return JsonData.buildSuccess(result);
    }

    /**
     * 获取钱包信息
     */
    @Operation(summary = "获取钱包信息", description = "返回总余额、近7天收益及近7天流水明细", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
    })
    @GetMapping("/wallet")
    public JsonData<WalletVO> getWallet() {
        Customer customer = AuthInterceptor.threadLocal.get();
        if (customer == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }
        return JsonData.buildSuccess(customerTransactionService.getWallet(customer.getId()));
    }

    /**
     * 获取我的交易记录列表
     */
    @Operation(summary = "获取我的交易记录列表", description = "分页获取我的所有交易/钻石明细记录（充值、提现、任务奖励等），按交易时间倒序排列。", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
    })
    @GetMapping("/transactions")
    public JsonData<IPage<CustomerTransactionVO>> getMyTransactions(
            @Parameter(description = "页码 (从1开始) | Page number (1-indexed)", example = "1") @RequestParam(value = "page", defaultValue = "1") Integer page,
            @Parameter(description = "每页数量 | Page size", example = "10") @RequestParam(value = "size", defaultValue = "10") Integer size) {

        // 从ThreadLocal获取当前登录的客户
        Customer customer = AuthInterceptor.threadLocal.get();

        if (customer == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }

        IPage<CustomerTransactionVO> result = customerTransactionService.getCustomerTransactions(customer.getId(), page, size);
        return JsonData.buildSuccess(result);
    }
}
