package com.gabon.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gabon.admin.model.entity.Customer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 客户Mapper接口
 * Customer Mapper
 */
@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {

    /**
     * 根据客户编码查询客户
     * @param code 客户编码
     * @return 客户信息
     */
    @Select("SELECT * FROM customers " +
            "WHERE code = #{code} AND deleted_flag IS NULL")
    Customer findByCode(@Param("code") String code);

    /**
     * 根据商家ID查询客户列表
     * @param businessId 商家ID
     * @return 客户列表
     */
    @Select("SELECT * FROM customers " +
            "WHERE business_id = #{businessId} AND deleted_flag IS NULL")
    List<Customer> findByBusinessId(@Param("businessId") Long businessId);

    /**
     * 根据VIP等级查询客户列表
     * @param vipLevel VIP等级
     * @return 客户列表
     */
    @Select("SELECT * FROM customers " +
            "WHERE vip_level = #{vipLevel} AND deleted_flag IS NULL")
    List<Customer> findByVipLevel(@Param("vipLevel") Integer vipLevel);
}

