package com.gabon.admin.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gabon.admin.model.dto.AdUploadUrlVO;
import com.gabon.admin.model.dto.AdvertisementResponse;
import com.gabon.admin.model.dto.AdvertiserResponse;
import com.gabon.admin.model.dto.CreateAdvertisementRequest;

public interface AdService {

    // ==================== 广告商 ====================

    List<AdvertiserResponse> findAdvertisers(Long id, String advertiserName, Integer status);

    void createAdvertiser(String advertiserName, String remark);

    void toggleAdvertiserStatus(Long id, Integer status);

    // ==================== 广告 ====================

    AdUploadUrlVO getUploadUrl(Long adminId, String fileName);

    IPage<AdvertisementResponse> findAdvertisements(int page, int size, Long id, String adName, Long advertiserId, String advertiserName, Integer status);

    void createAdvertisement(CreateAdvertisementRequest request);

    void toggleAdvertisementStatus(Long id, Integer status);
}
