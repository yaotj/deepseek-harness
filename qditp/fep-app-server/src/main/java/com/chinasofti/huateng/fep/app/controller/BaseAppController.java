package com.chinasofti.huateng.fep.app.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.model.app.ItpCommonFormRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;


abstract class BaseAppController {


    protected <T> T parseBizData(ItpCommonFormRequest request, Class<T> targetType) {
        String bizData = request == null ? null : request.getBizData();
        return JSON.parseObject(bizData == null || bizData.trim().isEmpty() ? "{}" : bizData, targetType);
    }


    protected String decodeBase64(String data) {
        try {
            return new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return data;
        }
    }


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
            return null;
        }
    }
}
