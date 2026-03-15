package com.gabon.common.model;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
@Data
public class PageBaseRequest {

    /** 当前页码，必须 >= 1 */
    @NotNull(message = "pageNum 不能为空")
    @Min(value = 1, message = "pageNum 必须 >= 1")
    private Integer pageNum;

    /** 每页条数，必须 >= 1 */
    @NotNull(message = "pageSize 不能为空")
    @Min(value = 1, message = "pageSize 必须 >= 1")
    private Integer pageSize;
}
