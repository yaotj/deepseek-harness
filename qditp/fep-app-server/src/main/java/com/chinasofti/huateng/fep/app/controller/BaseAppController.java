package com.chinasofti.huateng.fep.app.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.model.app.ItpCommonFormRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * APP FormData 接口的公共处理基类。
 *
 * <p>APP 将公共字段以表单字段提交，业务参数统一放在 {@code bizData} JSON 字符串中。
 * 子类仅负责接口语义和服务编排，本类负责将其反序列化为对应的业务 DTO。</p>
 */
abstract class BaseAppController {

    /**
     * 将 FormData 中的业务 JSON 转换为目标 DTO。
     *
     * <p>空 {@code bizData} 按空 JSON 对象处理，以保持历史接口的反序列化行为。</p>
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
     * 从原始回调报文中提取并解析 bizData，兼容三种形态：
     * <ol>
     *   <li>{@code bizData} 字段为 JSONObject（直接反序列化）</li>
     *   <li>{@code bizData} 字段为 Base64 编码的 JSON 字符串（解码后反序列化）</li>
     *   <li>无 {@code bizData} 字段（整体作为目标 DTO 反序列化）</li>
     * </ol>
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
