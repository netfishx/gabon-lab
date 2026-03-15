package com.gabon.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * MyBatis-Plus 自动填充处理器
 * Automatically fills audit fields (createTime, updateTime, etc.)
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        Instant now = Instant.now();
        // 注意：字段名必须与BaseDO中的字段名一致
        this.strictInsertFill(metaObject, "createTime", Instant.class, now);
        this.strictInsertFill(metaObject, "updateTime", Instant.class, now);
        this.strictInsertFill(metaObject, "deletedFlag", Instant.class, null); // NULL表示未删除
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", Instant.class, Instant.now());
    }
}
