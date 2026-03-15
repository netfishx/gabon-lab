package com.gabon.common.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * 通用基础数据对象，包含通用的审计字段
 */
@Data
public class BaseDO implements Serializable {

    /**
     * 主键ID
     */
    @TableId
    private Long id;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Instant createTime;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 修改时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updateTime;

    /**
     * 修改人
     */
    private String updateBy;
    /**
     * 逻辑删除标识（时间戳）
     * - `NULL` 表示可用 (未删除)
     * - **有值** 表示已删除 (删除时间戳)
     * 
     * 注意: 我们手动处理软删除，不使用@TableLogic
     * - 查询时: 手动添加 .isNull(Entity::getDeletedFlag)
     * - 删除时: 手动调用 entity.setDeletedFlag(Instant.now()); mapper.updateById(entity);
     */
    @TableField(fill = FieldFill.INSERT)
    private Instant deletedFlag;
}
