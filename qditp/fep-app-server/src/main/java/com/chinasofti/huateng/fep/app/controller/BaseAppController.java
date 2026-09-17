package com.chinasofti.huateng.fep.app.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.model.app.ItpCommonFormRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * APP FormData 接口的公共处理基类。
 */
abstract class BaseAppController {

    /**
     * 将 FormData 中的业务 JSON 转换为目标 DTO。
     *
     * <p>能被本方法解析的 DTO 就是对外契约，NEVER 加字段。</p>
     *
     * @param request    APP 公共 FormData 请求
     * @param targetType 业务 DTO 类型
     * @return 反序列化后的业务 DTO
     */
    protected <T> T parseBizData(ItpCommonFormRequest request, Class<T> targetType) {
        String bizData = request == null ? null : request.getBizData();
        return JSON.parseObject(bizData == null || bizData.trim().isEmpty() ? "{}" : bizData, targetType);
    }

    /**
     * 解码 Base64 字符串，若解码失败（非合法 Base64）则原样返回。
     */
    protected String decodeBase64(String data) {
        try {
            return new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return data;
        }
    }

    /**
     * 从原始回调报文中提取并解析 bizData。
     *
     * @param requestBody 回调报文原始字符串
     * @param targetType    目标 DTO 类型
     * @return 解析后的 DTO，解析失败返回 null
     */
    protected <T> T parseCallbackBody(String requestBody, Class<T> targetType) {
        try {
            JSONObject root = JSON.parseObject(requestBody);
            Object bizData = root.get("bizData");
            if (bizData instanceof JSONObject) {
                return ((JSONObject) bizData).toJavaObject(targetType);
            }
            if (bizData instanceof String && !((String) bizData).trim().isEmpty()) {
                return JSON.parseObject(decodeBase64((String) bizData), targetType);
            }
            return root.toJavaObject(targetType);
        } catch (Exception e) {
            // 由调用方 log 记录
            return null;
        }
    }
}
