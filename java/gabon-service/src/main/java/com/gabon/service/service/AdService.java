package com.gabon.service.service;

import com.gabon.service.model.vo.AdVO;

public interface AdService {

    /**
     * 随机获取一条可投放的广告，并原子扣减剩余次数
     * 若无可用广告返回 null
     */
    AdVO getRandomAd();
}
