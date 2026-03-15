package com.gabon.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabon.common.enums.BizCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;


/**
 * JsonData 响应对象，支持泛型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JsonData<T> {

    /**
     * 状态码 0 表示成功
     */
    private Integer code;

    /**
     * 数据
     */
    private T data;

    /**
     * 描述信息
     */
    private String msg;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     *  获取泛型数据
     *  @param typeReference 目标类型
     *  @return 解析后的数据
     */
    public T getData(TypeReference<T> typeReference) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("数据解析异常", e);
        }
    }

    /**
     * 成功，不传入数据
     * @return JsonData
     */
    public static <T> JsonData<T> buildSuccess() {
        return new JsonData<>(0, null, null);
    }

    /**
     * 成功，传入数据
     * @param data 数据
     * @return JsonData<T>
     */
    public static <T> JsonData<T> buildSuccess(T data) {
        return new JsonData<>(0, data, null);
    }

    /**
     * 失败，传入描述信息
     * @param msg 错误信息
     * @return JsonData<T>
     */
    public static <T> JsonData<T> buildError(String msg) {
        return new JsonData<>(-1, null, msg);
    }

    /**
     * 自定义状态码和错误信息
     * @param code 状态码
     * @param msg 错误信息
     * @return JsonData<T>
     */
    public static <T> JsonData<T> buildCodeAndMsg(int code, String msg) {
        return new JsonData<>(code, null, msg);
    }

    /**
     * 传入枚举，返回信息
     * @param codeEnum 业务枚举
     * @return JsonData<T>
     */
    public static <T> JsonData<T> buildResult(BizCodeEnum codeEnum) {
        return JsonData.buildCodeAndMsg(codeEnum.getCode(), codeEnum.getMessage());
    }


    /**
     * 实际下载文件的方法，返回 `ResponseEntity`
     */
    public static ResponseEntity<InputStreamResource> buildFileResponse(String fileName, byte[] fileData) {
        InputStreamResource resource = new InputStreamResource(new ByteArrayInputStream(fileData));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);
        headers.setContentType(MediaType.IMAGE_JPEG);

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(fileData.length)
                .body(resource);
    }
}
