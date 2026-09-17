package com.chinasofti.huateng.cardpool.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.chinasofti.huateng.cardpool.config.AccSecureProperties;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** ACC 安全接口签名工具。 */
public final class AccSignUtils {

    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private AccSignUtils() {
    }

    /**
     * 生成 ACC 要求的请求时间戳。
     *
     * @return yyyyMMddHHmmss 形式的当前时间
     */
    public static String buildTimestamp() {
        return LocalDateTime.now().format(TS_FORMATTER);
    }

    /**
     * 按 ACC 规格计算签名值。
     *
     * @param providerId 商户编码
     * @param charset    字符集
     * @param format     数据格式
     * @param timestamp  请求时间
     * @param deviceId   设备编码
     * @param signType   签名类型，00-不签名，02-MD5
     * @param bizData    业务参数
     * @param properties ACC 配置，取 signKey
     * @return 签名值；{@code signType=00} 时为空串
     */
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
        appendIfPresent(keyValuePairs, "bizData",
                JSON.toJSONString(bizData, JSONWriter.Feature.MapSortField));
        appendIfPresent(keyValuePairs, "charset", charset);
        appendIfPresent(keyValuePairs, "deviceId", deviceId);
        appendIfPresent(keyValuePairs, "format", format);
        appendIfPresent(keyValuePairs, "providerId", providerId);
        appendIfPresent(keyValuePairs, "signType", signType);
        appendIfPresent(keyValuePairs, "timestamp", timestamp);
        Collections.sort(keyValuePairs);
        String signValue = String.join("&", keyValuePairs);
        if ("02".equals(signType)) {
            if (!StringUtils.hasText(properties.getSignKey())) {
                throw new IllegalStateException("signType=02 但未配置 acc.secure.sign-key，拒绝降级为无密钥 MD5");
            }
            String tmpSignStr = signValue + "&key=" + properties.getSignKey();
            return DigestUtils.md5DigestAsHex(tmpSignStr.getBytes(StandardCharsets.UTF_8));
        }
        throw new IllegalArgumentException("暂不支持的signType: " + signType);
    }

    /**
     * 值非空时才参与签名拼接。
     *
     * @param keyValuePairs 拼接结果
     * @param key           参数名
     * @param value         参数值
     */
    private static void appendIfPresent(List<String> keyValuePairs, String key, String value) {
        if (StringUtils.hasText(value)) {
            keyValuePairs.add(key + "=" + value);
        }
    }
}
