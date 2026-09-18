package com.chinasofti.huateng.paysign.support;

import static com.chinasofti.huateng.paysign.support.PaySignValues.defaultString;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.chinasofti.huateng.model.app.ItpCommonRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** ITP **出向通知**（IF8B）的加签（2026-09-16 由当时的 {@code AppNotifyServiceImpl} 逐字搬出，ADR-D99；该类已于 ADR-D127 按聚合拆分）。 */
public final class AppNotifySigner {

    private static final Logger log = LoggerFactory.getLogger(AppNotifySigner.class);

    private AppNotifySigner() {
    }

    /**
     * 按 {@code signType} 生成 {@code sign}；不需要签名时返回 {@code null}。
     *
     * @param signKey ITP 与 APP 约定的签名密钥（{@code itp.signKey}）。<b>NEVER 把它写进日志。</b>
     */
    public static String buildItpSign(ItpCommonRequest<?> request, String signKey) {
        if (!StringUtils.hasText(request.getSignType()) || "00".equals(request.getSignType())) {
            return null;
        }
        String signSource = buildItpSignSource(request, signKey);
        return switch (request.getSignType()) {
            case "01" -> digest("SHA-1", signSource);
            case "02" -> digest("MD5", signSource);
            default -> null;
        };
    }

    private static String buildItpSignSource(ItpCommonRequest<?> request, String signKey) {
        List<String> parts = new ArrayList<>();
        appendIfPresent(parts, "bizData", JSON.toJSONString(request.getBizData(), JSONWriter.Feature.MapSortField));
        appendIfPresent(parts, "charset", request.getCharset());
        appendIfPresent(parts, "deviceId", request.getDeviceId());
        appendIfPresent(parts, "format", request.getFormat());
        appendIfPresent(parts, "providerId", request.getProviderId());
        appendIfPresent(parts, "signType", request.getSignType());
        appendIfPresent(parts, "timestamp", request.getTimestamp());
        Collections.sort(parts);
        return String.join("&", parts) + "&key=" + defaultString(signKey, "");
    }

    private static void appendIfPresent(List<String> parts, String key, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(key + "=" + value);
        }
    }

    private static String digest(String algorithm, String content) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
            byte[] bytes = messageDigest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            log.error("生成APP通知签名失败", e);
            return null;
        }
    }
}
