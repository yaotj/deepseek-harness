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

/**
 * ITP **出向通知**（IF8B）的加签（2026-09-16 由 {@code AppNotifyServiceImpl} 逐字搬出，ADR-D99）。
 *
 * <p><b>密钥 MUST 由调用方当参数传进来，本类 NEVER 自己持有 {@code itpSignKey}</b>：
 * 原实现里 {@code buildItpSignSource} 直接读 {@code @Value} 字段，这是它此前不能当纯函数外提的唯一原因。
 * 收成参数后本类仍满足 support 包那条条件式破例（纯函数、零状态、零依赖）。
 * <b>NEVER 给本类加 {@code @Value} / {@code @Component}</b> —— 那等于把密钥的持有点又多开一处。
 *
 * <p><b>算法与源串拼法一个字都不能改</b>（AGENTS.md §5.2 安全红线）：
 * {@code signType=00} 或空 ⇒ 不签；{@code 01} ⇒ SHA-1；{@code 02} ⇒ MD5；其余 ⇒ {@code null}。
 * 源串是「7 个字段按 {@code key=value} 收集后**字典序排序**、{@code &} 连接，末尾再拼 {@code &key=<密钥>}」，
 * 其中 {@code bizData} 用 {@code MapSortField} 序列化以保证同一份 bizData 恒得同一串。
 * <b>改动本类任何一行 MUST 人工复核安全合规性，并与对端重新联调验签。</b>
 *
 * <p><b>{@code digest} 失败返回 {@code null} 而不是抛异常</b>，沿用搬迁前的行为：
 * 通知加签失败时不阻断通知发送（对端可能配的是免签），只落一条 ERROR 日志。
 * <b>NEVER 改成抛异常</b> —— 那会让本已落库的通知任务在补偿链路里反复失败。
 * 注意 logger 名随本类变化，检索该 ERROR 日志 MUST 用类名 {@code AppNotifySigner}。
 */
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
