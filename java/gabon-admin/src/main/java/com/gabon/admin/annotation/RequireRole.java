package com.gabon.admin.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.gabon.common.enums.UserRole;

/**
 * 角色权限注解
 * Require Role Annotation
 * 
 * 用于限制接口访问权限，只有指定角色才能访问
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    /**
     * 需要的角色（满足其中一个即可）
     * Required roles (OR logic - any one of them is sufficient)
     */
    UserRole[] value();
    
    /**
     * 错误提示信息
     * Error message when permission denied
     */
    String message() default "无权限访问";
}

