package com.gabon.service.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gabon.service.model.entity.Customer;

/**
 * 客户Mapper
 * MyBatis映射器，用于操作customers表
 */
@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {

    /**
     * 根据ID查询未删除的用户
     */
    @Select("SELECT * FROM customers WHERE id = #{id} AND deleted_flag IS NULL")
    Customer selectActiveById(@Param("id") Long id);

    /**
     * 原子性增加钻石余额，避免并发读写竞态
     */
    @Update("UPDATE customers SET diamond_balance = COALESCE(diamond_balance, 0) + #{amount} WHERE id = #{id} AND deleted_flag IS NULL")
    int addDiamondBalance(@Param("id") Long id, @Param("amount") Long amount);

    /**
     * 统计用户的关注数（关注了多少人）
     */
    @Select("SELECT COUNT(*) FROM user_follow WHERE follower_id = #{userId} AND status = 1")
    Long countFollowingByUserId(@Param("userId") Long userId);

    /**
     * 统计用户的粉丝数（有多少人关注）
     */
    @Select("SELECT COUNT(*) FROM user_follow WHERE followed_id = #{userId} AND status = 1")
    Long countFollowersByUserId(@Param("userId") Long userId);
}
