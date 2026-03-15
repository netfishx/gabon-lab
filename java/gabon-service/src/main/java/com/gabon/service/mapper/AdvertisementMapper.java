package com.gabon.service.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gabon.service.model.entity.Advertisement;

@Mapper
public interface AdvertisementMapper extends BaseMapper<Advertisement> {

    /**
     * 查询所有可投放的广告（广告上架 + 广告商上架 + 剩余次数 > 0 + 未删除）
     */
    @Select("SELECT a.* FROM advertisement a " +
            "INNER JOIN advertiser adv ON a.advertiser_id = adv.id " +
            "WHERE a.status = 1 AND a.deleted_flag IS NULL AND a.remain_count > 0 " +
            "AND (a.expire_time IS NULL OR a.expire_time > NOW()) " +
            "AND adv.status = 1 AND adv.deleted_flag IS NULL")
    List<Advertisement> selectEligibleAds();

    /**
     * 扣减剩余次数
     * 返回影响行数：1=成功，0=已无剩余次数
     */
    @Update("UPDATE advertisement SET remain_count = remain_count - 1 WHERE id = #{id} AND remain_count > 0")
    int decrementRemainCount(@Param("id") Long id);
}
