package com.gabon.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gabon.admin.model.entity.Customer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    @Update("""
            UPDATE customers
            SET frozen_diamond_balance = COALESCE(frozen_diamond_balance, 0) - #{amount}
            WHERE id = #{id}
              AND deleted_flag IS NULL
              AND COALESCE(frozen_diamond_balance, 0) >= #{amount}
            """)
    int releaseFrozenDiamondBalance(@Param("id") Long id, @Param("amount") Long amount);

    @Update("""
            UPDATE customers
            SET diamond_balance = COALESCE(diamond_balance, 0) + #{amount}
            WHERE id = #{id}
              AND deleted_flag IS NULL
            """)
    int addDiamondBalance(@Param("id") Long id, @Param("amount") Long amount);

    @Update("""
            UPDATE customers
            SET diamond_balance = COALESCE(diamond_balance, 0) - #{amount},
                frozen_diamond_balance = COALESCE(frozen_diamond_balance, 0) - #{amount}
            WHERE id = #{id}
              AND deleted_flag IS NULL
              AND COALESCE(diamond_balance, 0) >= #{amount}
              AND COALESCE(frozen_diamond_balance, 0) >= #{amount}
            """)
    int settleWithdrawSuccess(@Param("id") Long id, @Param("amount") Long amount);
}
