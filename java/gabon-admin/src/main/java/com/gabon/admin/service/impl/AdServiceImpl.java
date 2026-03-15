package com.gabon.admin.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gabon.admin.mapper.AdvertisementMapper;
import com.gabon.admin.mapper.AdvertiserMapper;
import com.gabon.admin.model.dto.AdUploadUrlVO;
import com.gabon.admin.model.dto.AdvertisementResponse;
import com.gabon.admin.model.dto.AdvertiserResponse;
import com.gabon.admin.model.dto.CreateAdvertisementRequest;
import com.gabon.admin.model.entity.Advertisement;
import com.gabon.admin.model.entity.Advertiser;
import com.gabon.admin.service.AdService;
import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.exception.BizException;
import com.gabon.common.service.S3Service;
import com.gabon.common.util.CommonUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdServiceImpl implements AdService {

    private static final String AD_S3_PREFIX = "gabon/ads/";

    private static final Map<String, String> EXT_TO_CONTENT_TYPE = new HashMap<>();
    static {
        EXT_TO_CONTENT_TYPE.put(".jpg",  "image/jpeg");
        EXT_TO_CONTENT_TYPE.put(".jpeg", "image/jpeg");
        EXT_TO_CONTENT_TYPE.put(".png",  "image/png");
        EXT_TO_CONTENT_TYPE.put(".gif",  "image/gif");
        EXT_TO_CONTENT_TYPE.put(".webp", "image/webp");
    }


    private final AdvertiserMapper advertiserMapper;
    private final AdvertisementMapper advertisementMapper;
    private final S3Service s3Service;

    // ==================== 广告商 ====================

    @Override
    public List<AdvertiserResponse> findAdvertisers(Long id, String advertiserName, Integer status) {
        LambdaQueryWrapper<Advertiser> wrapper = new LambdaQueryWrapper<Advertiser>()
                .isNull(Advertiser::getDeletedFlag);

        if (id != null) {
            wrapper.eq(Advertiser::getId, id);
        }
        if (StringUtils.hasText(advertiserName)) {
            wrapper.like(Advertiser::getAdvertiserName, advertiserName);
        }
        if (status != null) {
            wrapper.eq(Advertiser::getStatus, status);
        }
        wrapper.orderByDesc(Advertiser::getCreateTime);

        return advertiserMapper.selectList(wrapper).stream()
                .map(this::convertToAdvertiserResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void createAdvertiser(String advertiserName, String remark) {
        boolean exists = advertiserMapper.exists(new LambdaQueryWrapper<Advertiser>()
                .eq(Advertiser::getAdvertiserName, advertiserName)
                .isNull(Advertiser::getDeletedFlag));
        if (exists) {
            throw new BizException(BizCodeEnum.ADVERTISER_NAME_DUPLICATE);
        }
        Advertiser advertiser = new Advertiser();
        advertiser.setAdvertiserName(advertiserName);
        advertiser.setStatus(0);
        advertiser.setRemark(remark);
        advertiserMapper.insert(advertiser);
        log.info("Advertiser created - ID: {}, Name: {}", advertiser.getId(), advertiser.getAdvertiserName());
    }

    @Override
    @Transactional
    public void toggleAdvertiserStatus(Long id, Integer status) {
        Advertiser advertiser = advertiserMapper.selectOne(
                new LambdaQueryWrapper<Advertiser>()
                        .eq(Advertiser::getId, id)
                        .isNull(Advertiser::getDeletedFlag));
        if (advertiser == null) {
            throw new BizException(BizCodeEnum.ADVERTISER_NOT_FOUND);
        }
        advertiserMapper.update(null, new LambdaUpdateWrapper<Advertiser>()
                .eq(Advertiser::getId, id)
                .set(Advertiser::getStatus, status)
                .set(Advertiser::getUpdateTime, Instant.now()));

        // 广告商下架时，级联下架名下所有广告
        if (status == 0) {
            advertisementMapper.update(null, new LambdaUpdateWrapper<Advertisement>()
                    .eq(Advertisement::getAdvertiserId, id)
                    .isNull(Advertisement::getDeletedFlag)
                    .set(Advertisement::getStatus, 0)
                    .set(Advertisement::getUpdateTime, Instant.now()));
            log.info("Advertiser offline - cascaded to advertisements, Advertiser ID: {}", id);
        }

        log.info("Advertiser status toggled - ID: {}, Status: {}", id, status);
    }

    // ==================== 广告 ====================

    @Override
    public AdUploadUrlVO getUploadUrl(Long adminId, String fileName) {
        String extension = (fileName != null && fileName.contains("."))
                ? fileName.substring(fileName.lastIndexOf(".")).toLowerCase()
                : "";
        String contentType = EXT_TO_CONTENT_TYPE.get(extension);
        if (contentType == null) {
            throw new BizException(BizCodeEnum.FILE_NOT_SUPPORT);
        }

        String s3Key = String.format("%s%d/%s%s", AD_S3_PREFIX, adminId, CommonUtil.generateUUID(), extension);
        String uploadUrl = s3Service.generatePresignedUploadUrl(s3Key, contentType, 15);
        String resourceUrl = s3Service.buildPublicUrl(s3Key);

        AdUploadUrlVO vo = new AdUploadUrlVO();
        vo.setUploadUrl(uploadUrl);
        vo.setResourceUrl(resourceUrl);
        return vo;
    }

    @Override
    public IPage<AdvertisementResponse> findAdvertisements(int page, int size, Long id, String adName,
            Long advertiserId, String advertiserName, Integer status) {
        // 按广告商名称模糊搜索时，先查出匹配的广告商ID集合
        Set<Long> advertiserIdFilter = null;
        if (StringUtils.hasText(advertiserName)) {
            List<Long> matchedIds = advertiserMapper.selectList(
                    new LambdaQueryWrapper<Advertiser>()
                            .like(Advertiser::getAdvertiserName, advertiserName)
                            .isNull(Advertiser::getDeletedFlag))
                    .stream().map(Advertiser::getId).collect(Collectors.toList());
            if (matchedIds.isEmpty()) {
                return new Page<>(page, size);
            }
            advertiserIdFilter = new java.util.HashSet<>(matchedIds);
        }
        if (advertiserId != null) {
            if (advertiserIdFilter != null) {
                advertiserIdFilter.retainAll(Set.of(advertiserId));
                if (advertiserIdFilter.isEmpty()) {
                    return new Page<>(page, size);
                }
            } else {
                advertiserIdFilter = Set.of(advertiserId);
            }
        }

        LambdaQueryWrapper<Advertisement> wrapper = new LambdaQueryWrapper<Advertisement>()
                .isNull(Advertisement::getDeletedFlag);

        if (id != null) {
            wrapper.eq(Advertisement::getId, id);
        }
        if (StringUtils.hasText(adName)) {
            wrapper.like(Advertisement::getAdName, adName);
        }
        if (advertiserIdFilter != null) {
            wrapper.in(Advertisement::getAdvertiserId, advertiserIdFilter);
        }
        if (status != null) {
            wrapper.eq(Advertisement::getStatus, status);
        }
        wrapper.orderByDesc(Advertisement::getCreateTime);

        IPage<Advertisement> pageResult = advertisementMapper.selectPage(new Page<>(page, size), wrapper);

        // 批量查询广告商名称
        Set<Long> adverIds = pageResult.getRecords().stream()
                .map(Advertisement::getAdvertiserId)
                .collect(Collectors.toSet());
        Map<Long, String> advertiserNameMap = new HashMap<>();
        if (!adverIds.isEmpty()) {
            advertiserMapper.selectList(
                    new LambdaQueryWrapper<Advertiser>()
                            .in(Advertiser::getId, adverIds)
                            .isNull(Advertiser::getDeletedFlag))
                    .forEach(a -> advertiserNameMap.put(a.getId(), a.getAdvertiserName()));
        }

        return pageResult.convert(ad -> convertToAdvertisementResponse(ad, advertiserNameMap));
    }

    @Override
    public void createAdvertisement(CreateAdvertisementRequest request) {
        Advertiser advertiser = advertiserMapper.selectOne(
                new LambdaQueryWrapper<Advertiser>()
                        .eq(Advertiser::getId, request.getAdvertiserId())
                        .isNull(Advertiser::getDeletedFlag));
        if (advertiser == null) {
            throw new BizException(BizCodeEnum.ADVERTISER_NOT_FOUND);
        }

        Advertisement ad = new Advertisement();
        ad.setAdName(request.getAdName());
        ad.setAdvertiserId(request.getAdvertiserId());
        ad.setResourceUrl(request.getResourceUrl());
        ad.setResourceType(1);
        ad.setJumpUrl(request.getJumpUrl());
        ad.setRemainCount(request.getCount());
        ad.setExpireTime(parseDateToEndOfDay(request.getExpireDate()));
        ad.setTotalCount(request.getCount());
        ad.setStatus(0);
        advertisementMapper.insert(ad);
        log.info("Advertisement created - ID: {}, Name: {}, Count: {}", ad.getId(), ad.getAdName(), ad.getTotalCount());
    }

    @Override
    public void toggleAdvertisementStatus(Long id, Integer status) {
        Advertisement ad = advertisementMapper.selectOne(
                new LambdaQueryWrapper<Advertisement>()
                        .eq(Advertisement::getId, id)
                        .isNull(Advertisement::getDeletedFlag));
        if (ad == null) {
            throw new BizException(BizCodeEnum.ADVERTISEMENT_NOT_FOUND);
        }
        advertisementMapper.update(null, new LambdaUpdateWrapper<Advertisement>()
                .eq(Advertisement::getId, id)
                .set(Advertisement::getStatus, status)
                .set(Advertisement::getUpdateTime, Instant.now()));
        log.info("Advertisement status toggled - ID: {}, Status: {}", id, status);
    }
    /**
     * 将日期字符串（yyyy-MM-dd）转为当天 23:59:59 的北京时间 Instant
     * null 输入返回 null（表示永不过期）
     */
    private Instant parseDateToEndOfDay(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        return LocalDate.parse(dateStr)
                .atTime(LocalTime.MAX)
                .atZone(ZoneId.of("Asia/Shanghai"))
                .toInstant();
    }

    private AdvertiserResponse convertToAdvertiserResponse(Advertiser advertiser) {
        AdvertiserResponse response = new AdvertiserResponse();
        response.setId(advertiser.getId());
        response.setAdvertiserName(advertiser.getAdvertiserName());
        response.setStatus(advertiser.getStatus());
        response.setCreateTime(advertiser.getCreateTime());
        response.setUpdateTime(advertiser.getUpdateTime());
        return response;
    }

    private AdvertisementResponse convertToAdvertisementResponse(Advertisement ad, Map<Long, String> advertiserNameMap) {
        AdvertisementResponse response = new AdvertisementResponse();
        response.setId(ad.getId());
        response.setAdName(ad.getAdName());
        response.setAdvertiserId(ad.getAdvertiserId());
        response.setAdvertiserName(advertiserNameMap.get(ad.getAdvertiserId()));
        response.setResourceUrl(ad.getResourceUrl());
        response.setJumpUrl(ad.getJumpUrl());
        response.setRemainCount(ad.getRemainCount());
        response.setExpireTime(ad.getExpireTime());
        response.setTotalCount(ad.getTotalCount());
        response.setStatus(ad.getStatus());
        response.setCreateTime(ad.getCreateTime());
        response.setUpdateTime(ad.getUpdateTime());
        return response;
    }
}
