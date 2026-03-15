package com.gabon.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HttpUtil {
    private static final Logger logger = LoggerFactory.getLogger(HttpUtil.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // **超时时间（单位：秒）**

    /**
     * 生成 HTTP 客户端，带超时时间
     */
    private static CloseableHttpClient createHttpClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                //返回数据的超时时间
                .setResponseTimeout(Timeout.ofSeconds(5))
                //连接上服务器的超时时间
                .setConnectTimeout(Timeout.ofSeconds(10))
                //从连接池中获取连接的超时时间
                .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                .build();

        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    /**
     * 发送 GET 请求
     */
    public static String doGet(String url) throws IOException {
        logger.info("【HTTP GET 请求】URL: {}", url);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                return handleResponse(response, "GET", url, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 发送 POST 请求（JSON）
     */
    public static String doPost(String url, Object requestBody) throws IOException {
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        logger.info("【HTTP POST 请求】URL: {}, Body: {}", url, jsonBody);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(url);
            request.setEntity(new StringEntity(objectMapper.writeValueAsString(requestBody), ContentType.APPLICATION_JSON));
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                return handleResponse(response, "POST", url, jsonBody);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 发送 PUT 请求（JSON）
     */
    public static String doPut(String url, Object requestBody) throws IOException {
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        logger.info("【HTTP PUT 请求】URL: {}, Body: {}", url, jsonBody);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPut request = new HttpPut(url);
            request.setEntity(new StringEntity(objectMapper.writeValueAsString(requestBody), ContentType.APPLICATION_JSON));
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                return handleResponse(response, "PUT", url, jsonBody);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 发送 DELETE 请求
     */
    public static String doDelete(String url) throws IOException {
        logger.info("【HTTP DELETE 请求】URL: {}", url);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpDelete request = new HttpDelete(url);
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                return handleResponse(response, "DELETE", url, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 处理响应，记录日志
     */
    public static String handleResponse(ClassicHttpResponse response, String method, String url, String requestBody) throws Exception {
        int statusCode = response.getCode();
        HttpEntity entity = response.getEntity();
        String responseBody = entity != null ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : null;

        if (statusCode >= 200 && statusCode < 300) {
            logger.info("【HTTP {} 响应】URL: {}, Status: {}, Response: {}", method, url, statusCode, responseBody);
        } else {
            logger.error("【HTTP {} 失败】URL: {}, Status: {}, Response: {}", method, url, statusCode, responseBody);
            throw new Exception("系统繁忙");
        }

        return responseBody;
    }
}
