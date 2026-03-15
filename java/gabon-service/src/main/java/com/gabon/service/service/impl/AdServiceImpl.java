package com.gabon.service.service.impl;

import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;

import com.gabon.service.mapper.AdvertisementMapper;
import com.gabon.service.model.entity.Advertisement;
import com.gabon.service.model.vo.AdVO;
import com.gabon.service.service.AdService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdServiceImpl implements AdService {

    private final AdvertisementMapper advertisementMapper;
    private final Random random = new Random();

    @Override
    public AdVO getRandomAd() {
        // 1. 查所有可投放广告
        List<Advertisement> eligible = advertisementMapper.selectEligibleAds();
        if (eligible.isEmpty()) {
            log.debug("No eligible ads available");
            return null;
        }

        // 2. 随机选一条
        Advertisement ad = eligible.get(random.nextInt(eligible.size()));

        // 3. 原子扣减，防止并发超扣
        int affected = advertisementMapper.decrementRemainCount(ad.getId());
        if (affected == 0) {
            // 并发抢占，该广告次数已耗尽
            log.debug("Ad remain_count exhausted by concurrent request - Ad ID: {}", ad.getId());
            return null;
        }

        log.debug("Ad served - ID: {}, Remain: {}", ad.getId(), ad.getRemainCount() - 1);
        return convertToVO(ad);
    }

    private AdVO convertToVO(Advertisement ad) {
        AdVO vo = new AdVO();
        vo.setId(ad.getId());
        vo.setAdName(ad.getAdName());
        vo.setResourceUrl(ad.getResourceUrl());
        vo.setJumpUrl(ad.getJumpUrl());
        return vo;
    }
}
