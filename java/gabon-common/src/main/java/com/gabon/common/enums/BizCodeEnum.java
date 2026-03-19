package com.gabon.common.enums;

import java.text.MessageFormat;

import com.gabon.common.exception.BizException;

import lombok.Getter;

/**

 **/
public enum BizCodeEnum {

    // ================== API 验签相关 ==================
    EXTERNAL_API_ERROR(200000, "外部 API 调用失败：{0}"),

    PARAM_ERROR(100001, "参数错误: {0}"),
    BUSINESS_NOT_FOUND(100042, "商户不存在: {0}"),

    // ================== HMAC 验签相关 ==================
    MISSING_HMAC_HEADERS(100000, "缺少 HMAC 签名所需的请求头（X-Code-Id / X-Timestamp / X-Content-SHA256 / X-Signature）"),
    INVALID_MERCHANT_CODE(100001, "无效的商户标识（X-Code-Id 不存在）"),
    INVALID_TIMESTAMP_FORMAT(100002, "时间戳格式不正确，应为秒级或毫秒级整数"),
    REQUEST_EXPIRED(100003, "请求已过期，时间差超过 {0} 秒（当前={1}，请求={2}）"),
    BODY_HASH_MISMATCH(100004, "请求体哈希值不匹配，请检查 body 是否被篡改"),
    SIGNATURE_VERIFICATION_FAILED(100005, "签名校验失败，请确认签名算法、密钥与 signInput 拼接规则一致"),
    PERMISSION_NOT_GRANTED(100006, "权限不足，无法访问该资源"),
    NO_AUTH(100007, "没有用户身份"),

    // ================== 通用业务错误 ==================
    INVALID_PARAM(100006, "请求参数不合法或缺失"),
    MISSING_BUSINESS_CONTEXT(100007, "未识别到商户上下文（business 为空）"),
    CUSTOMER_NOT_FOUND(100008, "该客户不存在或已被删除"),
    TIME_RANGE_REQUIRED(100009, "startTimestamp / endTimestamp 不能为空"),
    TIME_RANGE_INVALID(100010, "endTimestamp 不能早于 startTimestamp"),
    LIMIT_OUT_OF_RANGE(100011, "limit 参数非法或超过允许范围"),
    ORDER_NOT_FOUND(100012, "订单不存在: {0}"),
    ORDER_REFUND_NOT_ALLOWED(100013, "订单当前状态不允许退款: {0}"),

    /**
     * 客户
     */
    CUSTOMER_REPEAT(230001, "客户已经存在"),
    CUSTOMER_NOT_EXIST(230002, "客户不存在"),
    SIGN_IN_ALREADY_TODAY(23003, "今天已签到"),

    /**
     * 验证码
     */
    CODE_TO_ERROR(240001, "接收号码不合规"),
    CODE_LIMITED(240002, "验证码发送过快"),
    CODE_ERROR(240003, "验证码错误"),
    CODE_CAPTCHA_ERROR(240101, "图形验证码错误"),

    /**
     * 账号
     */
    ACCOUNT_REPEAT(250001, "账号已经存在"),
    ACCOUNT_UNREGISTER(250002, "账号不存在"),
    ACCOUNT_PWD_ERROR(250003, "账号或者密码错误"),
    ACCOUNT_UNLOGIN(250004, "账号未登录"),
    CHECK_ADMIN(250005, "为管理员无法使用"),
    PASSWORD_WEAK(250006, "密码强度不足，必须至少6位且包含字母和数字"),
    INVALID_ROLE(250007, "角色值无效，必须是1(管理员)或2(普通用户)"),
    PASSWORD_MISMATCH(250008, "新密码和确认密码不一致"),
    PASSWORD_SAME_AS_OLD(250009, "新密码不能与旧密码相同"),
    WITHDRAWAL_PASSWORD_NOT_SET(250010, "未设置取款密码"),
    WITHDRAWAL_PASSWORD_ERROR(250011, "取款密码错误"),
    DIAMOND_BALANCE_NOT_ENOUGH(250012, "可提现钻石不足"),
    WITHDRAWAL_ORDER_PENDING(250013, "当前已有提现申请处理中"),
    CASH_ORDER_NOT_FOUND(250014, "资金订单不存在"),
    CASH_ORDER_STATUS_ERROR(250015, "资金订单状态不正确，无法进行该操作"),

    /**
     * 用户关注相关
     */
    FOLLOW_SELF_NOT_ALLOWED(250016, "不能关注自己"),
    FOLLOW_TARGET_NOT_EXIST(250017, "被关注用户不存在或已被删除"),
    FOLLOW_ALREADY_EXISTS(250018, "已经关注该用户"),
    FOLLOW_NOT_EXISTS(250019, "未关注该用户，无法取消关注"),

    /**
     * 视频点赞相关
     */
    VIDEO_NOT_FOUND(250020, "视频不存在或未审核通过"),
    VIDEO_ALREADY_LIKED(250021, "已经点赞该视频"),
    VIDEO_NOT_LIKED(250022, "未点赞该视频，无法取消点赞"),

    /**
     * 邀请码相关
     */
    INVITE_CODE_NOT_FOUND(250023, "邀请码不存在"),
    INVITE_SELF_NOT_ALLOWED(250024, "不能使用自己的邀请码"),

    /**
     * 广告商相关
     */
    ADVERTISER_NOT_FOUND(250025, "广告商不存在或已被删除"),
    ADVERTISER_NAME_DUPLICATE(250026, "广告商名称已存在"),

    /**
     * 广告相关
     */
    ADVERTISEMENT_NOT_FOUND(250027, "广告不存在或已被删除"),

    /**
     * 商家
     */
    BUSINESS_UNREGISTER(260001, "商家不存在"),
    BUSINESS_REPEAT(260002, "商家已经存在"),

    /**
     * 商品
     */
    GOOD_NOT_EXIST(270001, "商品不存在"),
    GOOD_STATUS_ERROR(270002, "商品状态不正确，无法进行该操作"),

    /**
     * 订单
     */
    ORDER_CONFIRM_PRICE_FAIL(280002, "创建订单-验价失败"),
    ORDER_CONFIRM_REPEAT(280008, "订单恶意-重复提交"),
    ORDER_CONFIRM_TOKEN_EQUAL_FAIL(280009, "订单令牌缺少"),
    ORDER_CONFIRM_NOT_EXIST(280010, "订单不存在"),
    ORDER_INSUFFICIENT_COINS(200011, "余额不足，请充值后尝试"),
    ORDER_MATERIAL_ERROR(200012, "请上传正确素材"),
    ORDER_STATUS_ERROR(280013, "订单状态不正确，无法进行该操作"),

    /**
     * 标签
     */
    TAG_REPEAT(290001, "标签已经存在"),
    TAG_NOT_EXIST(290002, "标签不存在"),

    /**
     * 供应商
     */
    PROVIDER_REPEAT(310001, "供应商已经存在"),
    PROVIDER_NOT_EXIST(310002, "供应商不存在"),
    PROVIDER_GOOD_NOT_EXIST(310003, "供应商商品不存在"),
    PROVIDER_GOOD_MUST(310004, "只能选取供应商商品"),

    /**
     * 支付
     */
    PAY_ORDER_FAIL(300001, "创建支付订单失败"),
    PAY_ORDER_CALLBACK_SIGN_FAIL(300002, "支付订单回调验证签失败"),
    PAY_ORDER_CALLBACK_NOT_SUCCESS(300003, "支付回调更新处理失败"),
    PAY_ORDER_NOT_EXIST(300005, "订单不存在"),
    PAY_ORDER_STATE_ERROR(300006, "订单状态不正常"),
    PAY_ORDER_PAY_TIMEOUT(300007, "订单支付超时"),
    PAY_POINT_NOT_ENOUGH(300008, "积分不足，请获取更多积分"),
    GET_BALANCE_FAIL(300009, "获取余额失败"),

    /**
     * 资源
     */
    RESOURCE_NOT_EXIST(600101, "资源不存在"),

    /**
     * 通用操作码
     */

    OPS_REPEAT(110001, "重复操作"),
    OPS_NETWORK_ADDRESS_ERROR(110002, "网络地址错误"),

    /**
     * 文件相关
     */
    FILE_UPLOAD_USER_IMG_FAIL(700101, "账户头像文件上传失败"),
    FILE_NOT_SUPPORT(700102, "文件格式不支持"),
    FILE_SIZE_OUT_OF_LIMIT(700103, "文件大小超出限制"),
    FILE_NOT_EXIST(700104, "文件不存在"),

    /**
     * 数据库路由信息
     */
    DB_ROUTE_NOT_FOUND(800101, "数据库找不到"),

    /**
     * MQ消费异常
     */
    MQ_CONSUME_EXCEPTION(900101, "消费者消费异常"),

    /**
     * 数据查询条数超过限制
     */
    DATA_OUT_OF_LIMIT_SIZE(400001, "查询条数超过限制"),

    /**
     * 数据查询超过最大跨度
     */
    DATA_OUT_OF_LIMIT_DATE(400002, "日期查询超过最大跨度");

    @Getter
    private String message;

    @Getter
    private int code;

    private BizCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    // 🔹 格式化模板
    public BizException format(Object... args) {
        return new BizException(this.code, MessageFormat.format(this.message, args));
    }
}
