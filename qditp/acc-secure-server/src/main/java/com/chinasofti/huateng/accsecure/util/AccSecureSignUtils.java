package com.chinasofti.huateng.accsecure.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.chinasofti.huateng.accsecure.config.AccSecureProperties;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ACC 签名工具。
 */
public final class AccSecureSignUtils {
    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private AccSecureSignUtils() {
    }

    public static String buildTimestamp() {
        return LocalDateTime.now().format(TS_FORMATTER);
    }

    public static String buildSign(String providerId,
                                   String charset,
                                   String format,
                                   String timestamp,
                                   String deviceId,
                                   String signType,
                                   Object bizData,
                                   AccSecureProperties properties) {
        if (!StringUtils.hasText(signType) || "00".equals(signType)) {
            return "";
        }
        List<String> keyValuePairs = new ArrayList<>();
        appendIfPresent(keyValuePairs, "bizData", JSON.toJSONString(bizData, SerializerFeature.MapSortField));
        appendIfPresent(keyValuePairs, "charset", charset);
        appendIfPresent(keyValuePairs, "deviceId", deviceId);
        appendIfPresent(keyValuePairs, "format", format);
        appendIfPresent(keyValuePairs, "providerId", providerId);
        appendIfPresent(keyValuePairs, "signType", signType);
        appendIfPresent(keyValuePairs, "timestamp", timestamp);
        Collections.sort(keyValuePairs);
        String signValue = String.join("&", keyValuePairs);
        if ("02".equals(signType)) {
            String tmpSignStr = signValue;
            if (StringUtils.hasText(properties.getSignKey())) {
                tmpSignStr = tmpSignStr + "&key=" + properties.getSignKey();
            }
            return DigestUtils.md5DigestAsHex(tmpSignStr.getBytes(StandardCharsets.UTF_8));
        }
        throw new IllegalArgumentException("暂不支持的signType: " + signType);
    }

    private static void appendIfPresent(List<String> keyValuePairs, String key, String value) {
        if (StringUtils.hasText(value)) {
            keyValuePairs.add(key + "=" + value);
        }
    }
}
