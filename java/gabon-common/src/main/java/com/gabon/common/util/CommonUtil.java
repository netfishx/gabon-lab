package com.gabon.common.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

 /**


 **/


@Slf4j
public class CommonUtil {
    /**
     * 获取ip
     *
     * @param request
     * @return
     */
    public static String getIpAddr(HttpServletRequest request) {
        String ipAddress = null;
        try {
            ipAddress = request.getHeader("x-forwarded-for");
            if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getHeader("Proxy-Client-IP");
            }
            if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getRemoteAddr();
                if (ipAddress.equals("127.0.0.1")) {
                    // 根据网卡取本机配置的IP
                    InetAddress inet = null;
                    try {
                        inet = InetAddress.getLocalHost();
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                    ipAddress = inet.getHostAddress();
                }
            }
            // 对于通过多个代理的情况，第一个IP为客户端真实IP,多个IP按照','分割
            if (ipAddress != null && ipAddress.length() > 15) {
                // "***.***.***.***".length()
                // = 15
                if (ipAddress.indexOf(",") > 0) {
                    ipAddress = ipAddress.substring(0, ipAddress.indexOf(","));
                }
            }
        } catch (Exception e) {
            ipAddress = "";
        }
        return ipAddress;
    }


    /**
     * 获取全部请求头
     *
     * @param request
     * @return
     */
    public static Map<String, String> getAllRequestHeader(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        Map<String, String> map = new HashMap<>();
        while (headerNames.hasMoreElements()) {
            String key = (String) headerNames.nextElement();
            //根据名称获取请求头的值
            String value = request.getHeader(key);
            map.put(key, value);
        }

        return map;
    }


    /**
     * MD5加密
     *
     * @param data
     * @return
     */
    public static String MD5(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] array = md.digest(data.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte item : array) {
                sb.append(Integer.toHexString((item & 0xFF) | 0x100).substring(1, 3));
            }

            return sb.toString().toUpperCase();
        } catch (Exception exception) {
        }
        return null;

    }


    /**
     * 获取验证码随机数
     *
     * @param length
     * @return
     */
    public static String getRandomCode(int length) {

        String sources = "0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < length; j++) {
            sb.append(sources.charAt(random.nextInt(9)));
        }
        return sb.toString();
    }


    /**
     * 获取当前时间戳
     *
     * @return
     */
    public static long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }


    /**
     * 生成uuid
     *
     * @return
     */
    public static String generateUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "").substring(0, 32);
    }

    /**
     * 获取随机长度的串
     *
     * @param length
     * @return
     */
    private static final String ALL_CHAR_NUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    public static String getStringNumRandom(int length) {
        //生成随机数字和字母,
        Random random = new Random();
        StringBuilder saltString = new StringBuilder(length);
        for (int i = 1; i <= length; ++i) {
            saltString.append(ALL_CHAR_NUM.charAt(random.nextInt(ALL_CHAR_NUM.length())));
        }
        return saltString.toString();
    }


    /**
     * 响应json数据给前端
     *
     * @param response
     * @param obj
     */
    public static void sendJsonMessage(HttpServletResponse response, Object obj) {

        response.setContentType("application/json; charset=utf-8");

        try (PrintWriter writer = response.getWriter()) {
            writer.print(JsonUtil.obj2Json(obj));
            response.flushBuffer();

        } catch (IOException e) {
            log.warn("响应json数据给前端异常:{}", e);
        }


    }

    /**
     * 响应HTML数据给前端
     *
     * @param response
     * @param jsonData
     */
    public static void sendHtmlMessage(HttpServletResponse response, JsonData jsonData) {

        response.setContentType("text/html; charset=utf-8");

        try (PrintWriter writer = response.getWriter()) {
            writer.write(jsonData.getData().toString());
            writer.flush();
        } catch (IOException e) {
            log.warn("响应json数据给前端异常:{}", e);
        }


    }

     /**
      * murmurHash32 算法实现，返回 long 类型（将 int 转为 long 保证无符号精度）
      * @param key 输入字符串
      * @return long 类型 hash 值
      */
     public static long murmurHash32(String key) {
         byte[] data = key.getBytes(StandardCharsets.UTF_8);
         int seed = 0x9747b28c;
         int m = 0x5bd1e995;
         int r = 24;

         int length = data.length;
         int h = seed ^ length;
         int i = 0;

         while (length >= 4) {
             int k = (data[i] & 0xff) |
                     ((data[i + 1] & 0xff) << 8) |
                     ((data[i + 2] & 0xff) << 16) |
                     ((data[i + 3] & 0xff) << 24);

             k *= m;
             k ^= k >>> r;
             k *= m;

             h *= m;
             h ^= k;

             i += 4;
             length -= 4;
         }

         switch (length) {
             case 3 -> h ^= (data[i + 2] & 0xff) << 16;
             case 2 -> h ^= (data[i + 1] & 0xff) << 8;
             case 1 -> {
                 h ^= (data[i] & 0xff);
                 h *= m;
             }
         }

         h ^= h >>> 13;
         h *= m;
         h ^= h >>> 15;

         // 返回 long（将 int 转换为无符号 long）
         return h & 0xffffffffL;
     }


    /**
     * URL增加前缀
     * @param url
     * @return
     */
    public static String addUrlPrefix(String url){

        return IDUtil.geneSnowFlakeID()+"&"+url;

    }

    /**
     * 移除URL前缀
     * @param url
     * @return
     */
    public static String removeUrlPrefix(String url){
        String originalUrl = url.substring(url.indexOf("&")+1);
        return originalUrl;
    }


    /**
     * 如果短链码重复，则调用这个方法
     * url前缀的编号递增1
     * 如果还是用雪花算法，则容易C端和B端不一致，所以采用编号递增1的方式
     *
     * 123132432212&https://cabbage.net/download.html
     *
     * @param url
     * @return
     */
    public static String addUrlPrefixVersion(String url){

        //随机id
        String version = url.substring(0,url.indexOf("&"));

        //原始地址
        String originalUrl = url.substring(url.indexOf("&")+1);

        //新id
        Long newVersion = Long.parseLong(version)+1;

        String newUrl = newVersion + "&"+originalUrl;

        return newUrl;
    }



 }