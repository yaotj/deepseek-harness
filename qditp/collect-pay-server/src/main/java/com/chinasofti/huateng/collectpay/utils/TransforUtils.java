package com.chinasofti.huateng.collectpay.utils;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;
import com.chinasofti.huateng.collectpay.model.request.PayCenterBaseRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class TransforUtils {

    /**
     * 将公共参数复制到业务请求DTO中。
     */
    public static  <T extends BaseRequestDTO> T copyBaseParams(BaseRequestDTO baseRequest, Class<T> clazz) {
        T request = JSON.parseObject(baseRequest.getBizData(), clazz);
        request.setProviderId(baseRequest.getProviderId());
        request.setCharset(baseRequest.getCharset());
        request.setFormat(baseRequest.getFormat());
        request.setTimestamp(baseRequest.getTimestamp());
        // Some TVM requests carry deviceId in bizData; do not erase it when the
        // optional common parameter is omitted or blank.
        if (baseRequest.getDeviceId() != null && !baseRequest.getDeviceId().isBlank()) {
            request.setDeviceId(baseRequest.getDeviceId());
        }
        request.setSignType(baseRequest.getSignType());
        request.setSign(baseRequest.getSign());
        request.setBizData(baseRequest.getBizData());
        return request;
    }

    public static String getStringFromData(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    public static  <T extends PayCenterBaseRequestDTO> T paycentercopyBaseParams(PayCenterBaseRequestDTO baseRequest, Class<T> clazz) {
        T request = JSON.parseObject(baseRequest.getBizData(), clazz);
        request.setMerchantNo(baseRequest.getMerchantNo());
        request.setApiVersion(baseRequest.getApiVersion());
        request.setSignType(baseRequest.getSignType());
        request.setCharset(baseRequest.getCharset());
        request.setSign(baseRequest.getSign());
        request.setBizData(baseRequest.getBizData());
        return request;
    }

}
