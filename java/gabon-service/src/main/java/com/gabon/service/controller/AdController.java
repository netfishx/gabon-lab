package com.gabon.service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabon.service.config.AuthInterceptor;
import com.gabon.service.model.entity.Customer;
import com.gabon.service.model.vo.AdVO;
import com.gabon.service.service.AdService;
import com.gabon.service.service.TaskProgressService;
import com.gabon.common.util.JsonData;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/ads")
@RequiredArgsConstructor
@Tag(name = "广告", description = "Advertisement APIs")
public class AdController {

    private final AdService adService;
    private final TaskProgressService taskProgressService;

    @Operation(
            summary = "获取随机广告（并自动上报观看）",
            description = "随机返回一条可投放广告并扣减剩余次数。游客可访问；若携带有效 token，后端会自动为当前用户增加每日观看广告任务进度。无可用广告时 data 为 null"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "请求成功，data 为广告信息或 null（无可用广告）")
    })
    @GetMapping("/watch")
    public JsonData<AdVO> getRandomAd() {
        AdVO ad = adService.getRandomAd();
        if (ad != null) {
            Customer customer = AuthInterceptor.threadLocal.get();
            if (customer != null) {
                Long customerId = customer.getId();
                log.info("Ad fetched and watch reported for customer: {}", customerId);
                taskProgressService.updateWatchAdProgress(customerId);
            }
        }
        return JsonData.buildSuccess(ad);
    }
}
