package com.chinasofti.huateng.account.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.chinasofti.huateng.account.model.ItpSignProperties;
import com.chinasofti.huateng.model.app.ItpCommonRequest;
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
 *
 * <p><b>⚠️ 本类当前没有任何调用点</b>（ADR-D35 / ADR-D37 复核：全模块 grep 只命中
 * {@code RequestApplicationController} 的构造器参数），因此账户域 24 个端点全部裸暴露。
 * 补验签见 ADR-D35，<b>NEVER 因为「没人用」就删掉本类</b>。</p>
 *
 * <p><b>⚠️ 本类用的是 fastjson <b>1</b>（{@code com.alibaba.fastjson}），而 AGENTS.md §5.1 要求
 * 统一 Fastjson2</b>。ADR-D37 <b>刻意没有替换</b>：{@code buildSignSource} 用
 * {@code SerializerFeature.MapSortField} 决定 {@code bizData} 的序列化字节，换库会改变
 * 签名源串 ⇒ 已发出的 sign 全部失配。这属 §5.2「安全红线：NEVER 擅自修改现有加密/签名逻辑」，
 * <b>要换 MUST 与上游同批改并端到端比对签名</b>。</p>
 */
@Component
public class AccountRequestVerifier {
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\d{14}");

    /**
     * 校验与验签配置（ADR-D37 由 4 个 {@code @Value} 收成一个对象，配置键与默认值未变）。
     *
     * <p><b>{@code signKey} 仍是明文默认值，上线前 MUST 改成 {@code ${ITP_SIGN_KEY:}} 并轮换</b>，
     * 详见 {@link ItpSignProperties} 类注释。</p>
     */
    private final ItpSignProperties signProperties;

    /** 构造器注入（ADR-D37）。依赖全部 final，漏注入在编译期即报错。 */
    public AccountRequestVerifier(ItpSignProperties signProperties) {
        this.signProperties = signProperties;
    }

    public String validateCommonRequest(ItpCommonRequest<?> request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getProviderId())) {
            return "providerId不能为空";
        }
        if (!signProperties.getProviderId().equals(request.getProviderId())) {
            return "providerId非法";
        }
        if (!StringUtils.hasText(request.getCharset())) {
            return "charset不能为空";
        }
        if (!signProperties.getCharset().equalsIgnoreCase(request.getCharset())) {
            return "charset非法";
        }
        if (!StringUtils.hasText(request.getFormat())) {
            return "format不能为空";
        }
        if (!signProperties.getFormat().equalsIgnoreCase(request.getFormat())) {
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

    public boolean checkSign(ItpCommonRequest<?> request) {
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

    private String buildSignSource(ItpCommonRequest<?> request) {
        List<String> parts = new ArrayList<>();
        appendIfPresent(parts, "bizData", JSON.toJSONString(request.getBizData(), SerializerFeature.MapSortField));
        appendIfPresent(parts, "charset", request.getCharset());
        appendIfPresent(parts, "deviceId", request.getDeviceId());
        appendIfPresent(parts, "format", request.getFormat());
        appendIfPresent(parts, "providerId", request.getProviderId());
        appendIfPresent(parts, "signType", request.getSignType());
        appendIfPresent(parts, "timestamp", request.getTimestamp());
        Collections.sort(parts);
        return String.join("&", parts) + "&key=" + signProperties.getSignKey();
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
