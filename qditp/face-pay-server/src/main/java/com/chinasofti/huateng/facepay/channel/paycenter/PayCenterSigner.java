package com.chinasofti.huateng.facepay.channel.paycenter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 支付中心签名。<b>纯 Java 类，不带 Spring 注解</b>，可直接 new 出来做单测（见
 * {@code PayCenterMessageFactoryTest}）。
 *
 * <h2>待签串的口径：对齐 pay-sign-server（用户 2026-09-09 裁决）</h2>
 * <pre>
 * 把 bizData 的原始 JSON 解成 TreeMap（键名字典序）→ 跳过 null 与空白值
 * → key=value 用 &amp; 拼接
 * 例：amount=600&amp;merchantOrderNo=F20026...&amp;subject=地铁单程票
 * </pre>
 * 来源 {@code PayGatewayClient.buildSignSource:111-124}。<b>信封的 merchantNo / apiVersion /
 * signType / charset 四个字段不参与待签串</b>。
 *
 * <h2>为什么换掉了 collect-pay 那一套</h2>
 * <p>两个模块打的是<b>完全相同的三条 URL</b>（`payment/requestPay`、`payment/payQuery`、
 * `refund/requestRefund`，见 `pay-sign-server/application.properties:84-86` 与
 * `collect-pay-server/application.yml:28-30`），却用了两套待签串：</p>
 * <ul>
 *   <li>`collect-pay-server`（{@code SignUtils.buildSignData}）**签外层信封**：
 *       {@code merchantNo=..&apiVersion=..&signType=..&charset=..&bizData=<整个Base64串>}，
 *       固定 5 键、不排序、不剔空；</li>
 *   <li>`pay-sign-server` **签 bizData 内部字段**，即本类现在的实现。</li>
 * </ul>
 * <p>两套没有交集（一个签壳、一个签芯），而两条链路都在生产跑通过，
 * 说明至少有一条的验签在网关侧实际未生效。用户裁决<b>以 pay-sign-server 为准，
 * 后续联调失败再回退</b>；回退方式就是把 {@link #buildSignSource} 换回信封五键拼接，
 * 并把 {@link #sign} 的第二个入参去掉。</p>
 *
 * <p>{@code bizData} 本身仍是 Base64（两个模块一致，见
 * {@code PayGatewayClient.buildRequest:98}），本次只改「签什么」，没改「发什么」。</p>
 *
 * <p>私钥与聚合码密钥 NEVER 进日志。旧实现 {@code SignUtils:33} 有一行
 * {@code log.info("privateKey is {}", privateKey)}，本类不予保留。</p>
 */
public class PayCenterSigner {

    private final String privateKeyBase64;

    private final String signAlgorithm;

    private final String aggregateCodeKey;

    public PayCenterSigner(String privateKeyBase64, String signAlgorithm, String aggregateCodeKey) {
        this.privateKeyBase64 = privateKeyBase64;
        this.signAlgorithm = signAlgorithm;
        this.aggregateCodeKey = aggregateCodeKey;
    }

    /**
     * 对信封签名，写回 {@code sign} 字段。签名失败抛异常，NEVER 返回空串蒙混过关。
     *
     * @param bizDataJson <b>Base64 之前的原始 JSON</b>。待签串取自它的内部字段，
     *                    因此不能传 {@code request.getBizData()}（那已经是 Base64 后的串）
     */
    public void sign(PayCenterRequest request, String bizDataJson) {
        if (privateKeyBase64 == null || privateKeyBase64.isBlank()) {
            throw new IllegalStateException("pay.center.private-key 未配置，无法签名");
        }
        request.setSign(rsaSign(buildSignSource(bizDataJson)));
    }

    /**
     * 待签串组装。单独暴露成静态方法，便于单测逐字比对，不必真的持有私钥。
     *
     * <p>规则见类注释：{@code TreeMap} 键名字典序、跳过 null 与空白值、
     * 嵌套结构按键排序后序列化。</p>
     *
     * @param bizDataJson Base64 之前的原始 JSON
     */
    public static String buildSignSource(String bizDataJson) {
        if (bizDataJson == null || bizDataJson.isBlank()) {
            throw new IllegalArgumentException("bizDataJson 为空，无法组装待签串");
        }
        Map<String, Object> params = JSON.parseObject(bizDataJson, TreeMap.class);
        StringBuilder source = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            if (value == null || String.valueOf(value).isBlank()) {
                continue;
            }
            if (!source.isEmpty()) {
                source.append('&');
            }
            source.append(entry.getKey()).append('=').append(signValue(value));
        }
        return source.toString();
    }

    /** 嵌套对象/数组按键排序后序列化，与 {@code PayGatewayClient.signValue} 一致。 */
    private static String signValue(Object value) {
        return value instanceof Map || value instanceof List
                ? JSON.toJSONString(value, JSONWriter.Feature.MapSortField)
                : String.valueOf(value);
    }

    /**
     * 聚合码 URL 的签名：{@code md5("orderNo=" + orderNo + "&key=" + jhmKey)}。
     *
     * <p>照搬旧 {@code SignUtils.getJhmSign} + {@code getSign}：只有一个参数，因此 TreeMap
     * 排序无实际作用；空串参数会被跳过，这里 {@code orderNo} 必填，故不做该分支。</p>
     */
    public String aggregateCodeSign(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("orderNo 为空，无法生成聚合码签名");
        }
        if (aggregateCodeKey == null || aggregateCodeKey.isBlank()) {
            throw new IllegalStateException("pay.center.jhm-key 未配置，无法生成聚合码签名");
        }
        return md5Hex("orderNo=" + orderNo + "&key=" + aggregateCodeKey);
    }

    private String rsaSign(String source) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            Signature signature = Signature.getInstance(signAlgorithm);
            signature.initSign(key);
            signature.update(source.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("支付中心报文签名失败, algorithm=" + signAlgorithm, e);
        }
    }

    private static String md5Hex(String source) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("MD5")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("聚合码签名计算失败", e);
        }
    }
}
