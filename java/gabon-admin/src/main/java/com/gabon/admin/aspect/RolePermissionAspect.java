package com.gabon.admin.aspect;

import com.gabon.admin.annotation.RequireRole;
import com.gabon.admin.config.AuthInterceptor;
import com.gabon.admin.model.entity.AdminUser;
import com.gabon.common.enums.UserRole;
import com.gabon.common.exception.BizException;
import com.gabon.common.enums.BizCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 角色权限切面
 * Role Permission Aspect
 */
@Slf4j
@Aspect
@Component
public class RolePermissionAspect {

    @Before("@annotation(com.gabon.admin.annotation.RequireRole)")
    public void checkPermission(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireRole requireRole = method.getAnnotation(RequireRole.class);

        if (requireRole != null) {
            UserRole[] requiredRoles = requireRole.value();
            String message = requireRole.message();

            log.debug("Checking permission for method: {}, required roles: {}",
                    method.getName(), (Object) requiredRoles);

            // Get current user from ThreadLocal
            AdminUser currentUser = AuthInterceptor.threadLocal.get();
            if (currentUser == null) {
                log.warn("No authenticated user found in ThreadLocal");
                throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
            }

            // Check if user's role matches any of the required roles
            Integer userRole = currentUser.getRole();
            boolean hasPermission = Arrays.stream(requiredRoles)
                    .anyMatch(role -> role.getCode().equals(userRole));

            if (!hasPermission) {
                log.warn("Permission denied for user: {}, role: {}, required roles: {}",
                        currentUser.getId(), userRole, (Object) requiredRoles);
                throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN.getCode(),
                        message != null && !message.isEmpty() ? message : "无权限访问");
            }

            log.debug("Permission check passed for user: {}", currentUser.getId());
        }
    }
}
