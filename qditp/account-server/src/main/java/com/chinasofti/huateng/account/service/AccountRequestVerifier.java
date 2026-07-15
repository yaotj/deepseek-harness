package com.chinasofti.huateng.account.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.chinasofti.huateng.account.model.common.CommonRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 公共报文参数校验与验签。
 */
@Component
public class AccountRequestVerifier {
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\d{14}");

    @Value("${itp.providerId:01}")
    private String providerId;

    @Value("${itp.charset:UTF-8}")
    private String charset;

    @Value("${itp.format:json}")
    private String format;

    @Value("${itp.signKey:bc4f7c96259acf9946094fa}")
    private String signKey;

    public String validateCommonRequest(CommonRequest<?> request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getProviderId())) {
            return "providerId不能为空";
        }
        if (!providerId.equals(request.getProviderId())) {
            return "providerId非法";
        }
        if (!StringUtils.hasText(request.getCharset())) {
            return "charset不能为空";
        }
        if (!charset.equalsIgnoreCase(request.getCharset())) {
            return "charset非法";
        }
        if (!StringUtils.hasText(request.getFormat())) {
            return "format不能为空";
        }
        if (!format.equalsIgnoreCase(request.getFormat())) {
            return "format非法";
        }
        if (!StringUtils.hasText(request.getTimestamp())) {
            return "timestamp不能为空";
        }
        if (!TIMESTAMP_PATTERN.matcher(request.getTimestamp()).matches()) {
            return "timestamp格式非法";
        }
        if (!StringUtils.hasText(request.getSignType())) {
            return "signType不能为空";
        }
        if (!"00".equals(request.getSignType()) && !"01".equals(request.getSignType()) && !"02".equals(request.getSignType())) {
            return "signType非法";
        }
        if (!"00".equals(request.getSignType()) && !StringUtils.hasText(request.getSign())) {
            return "sign不能为空";
        }
        if (request.getBizData() == null) {
            return "bizData不能为空";
        }
        return null;
    }

    public boolean checkSign(CommonRequest<?> request) {
        if (request == null || !StringUtils.hasText(request.getSignType())) {
            return false;
        }
        if ("00".equals(request.getSignType())) {
            return true;
        }
        if (!StringUtils.hasText(request.getSign())) {
            return false;
        }
        String signSource = buildSignSource(request);
        String expectedSign = switch (request.getSignType()) {
            case "01" -> digest("SHA-1", signSource);
            case "02" -> digest("MD5", signSource);
            default -> null;
        };
        return StringUtils.hasText(expectedSign) && request.getSign().equalsIgnoreCase(expectedSign);
    }

    private String buildSignSource(CommonRequest<?> request) {
        List<String> parts = new ArrayList<>();
        appendIfPresent(parts, "bizData", JSON.toJSONString(request.getBizData(), SerializerFeature.MapSortField));
        appendIfPresent(parts, "charset", request.getCharset());
        appendIfPresent(parts, "deviceId", request.getDeviceId());
        appendIfPresent(parts, "format", request.getFormat());
        appendIfPresent(parts, "providerId", request.getProviderId());
        appendIfPresent(parts, "signType", request.getSignType());
        appendIfPresent(parts, "timestamp", request.getTimestamp());
        Collections.sort(parts);
        return String.join("&", parts) + "&key=" + signKey;
    }

    private void appendIfPresent(List<String> parts, String key, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(key + "=" + value);
        }
    }

    private String digest(String algorithm, String content) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
            byte[] bytes = messageDigest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
