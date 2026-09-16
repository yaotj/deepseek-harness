package com.chinasofti.huateng.facepay.channel.paycenter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报文组装与签名的口径测试。<b>不起 Spring</b>，全部直接 new。
 *
 * <p>这些断言的作用是把「与旧实现逐字对齐」钉住：bizData 的键、键序、场景差异、待签串形态。
 * 改动其中任何一条都会让支付中心验签失败或报文被拒，因此断言写的是字面量而不是常量引用。</p>
 */
class PayCenterMessageFactoryTest {

    private PayCenterProperties properties;

    private PayCenterMessageFactory factory;

    private PublicKey publicKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        this.publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(keyPair.getPublic().getEncoded()));

        properties = new PayCenterProperties();
        properties.setMerchantNo("MERCHANT001");
        properties.setPrivateKey(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        properties.setPayNoticeUrl("http://itp.example/ci/tvm/payNotice");
        properties.setCheckoutCounterUrl("http://pay.example/api/v1/checkoutCounter");
        properties.setRefundReason("出票数量不足");
        properties.setJhmKey("unit-test-jhm-key");

        factory = new PayCenterMessageFactory(properties,
                new PayCenterSigner(properties.getPrivateKey(), properties.getSignAlgorithm(),
                        properties.getJhmKey()));
    }

    @Test
    void qrcodePayRequestKeepsLegacyKeyOrderAndDefaultVendor() {
        PayCenterRequest request = factory.buildPayRequest(new PayCenterPayCommand(
                "F2F20260908001", PayScene.QRCODE, null, "0", 300L, "地铁单程票", "青岛地铁", null));

        assertEquals("{\"orderNo\":\"F2F20260908001\",\"scene\":\"qrcode\",\"paymentVendor\":\"0C\","
                        + "\"payType\":\"0\",\"amount\":300,\"industryType\":\"1\",\"subject\":\"地铁单程票\","
                        + "\"body\":\"青岛地铁\",\"orderTimeOut\":180,"
                        + "\"notifyUrl\":\"http://itp.example/ci/tvm/payNotice\"}",
                decodeBizData(request));
    }

    @Test
    void scanPayRequestCarriesAuthCodeAndOmitsPayType() {
        PayCenterRequest request = factory.buildPayRequest(new PayCenterPayCommand(
                "F2F20260908002", PayScene.SCAN, "0A", "0", 500L, "地铁单程票", "青岛地铁", "285xxxxxxxxxxxxx"));

        String bizData = decodeBizData(request);
        assertFalse(bizData.contains("payType"), "scene=scan 旧实现不传 payType");
        assertTrue(bizData.indexOf("\"authCode\"") < bizData.indexOf("\"notifyUrl\""),
                "scene=scan 的 authCode 在 notifyUrl 之前");
        assertTrue(bizData.contains("\"paymentVendor\":\"0A\""));
    }

    @Test
    void appPayRequestUsesAppScene() {
        PayCenterRequest request = factory.buildPayRequest(new PayCenterPayCommand(
                "F2F20260908003", PayScene.APP, "0B", "1", 200L, "取票", "青岛地铁", null));

        assertTrue(decodeBizData(request).contains("\"scene\":\"app\""));
        assertTrue(decodeBizData(request).contains("\"payType\":\"1\""));
    }

    @Test
    void queryRequestUsesMerchantOrderNoKey() {
        assertEquals("{\"merchantOrderNo\":\"F2F20260908001\"}",
                decodeBizData(factory.buildQueryRequest("F2F20260908001")));
    }

    @Test
    void refundRequestCarriesThreeOrderNumbers() {
        PayCenterRequest request = factory.buildRefundRequest(
                "RF20260908001", "F2F20260908001", "PC20260908999", 300L);

        assertEquals("{\"refundOrderNo\":\"RF20260908001\",\"merchantOrderNo\":\"F2F20260908001\","
                        + "\"orderNo\":\"PC20260908999\",\"refundAmount\":300,\"refundReason\":\"出票数量不足\"}",
                decodeBizData(request));
    }

    @Test
    void refundQueryRequestUsesMerchantRefundNoKey() {
        assertEquals("{\"merchantRefundNo\":\"RF20260908001\"}",
                decodeBizData(factory.buildRefundQueryRequest("RF20260908001")));
    }

    /**
     * 待签串取自 bizData 的<b>内部字段</b>（对齐 pay-sign-server，用户 2026-09-09 裁决）。
     * 查询报文只有一个 key，因此待签串就是 {@code orderNo=...}，信封字段一个都不出现。
     */
    @Test
    void signSourceComesFromBizDataFieldsAndIsVerifiableByPublicKey() throws Exception {
        PayCenterRequest request = factory.buildQueryRequest("F2F20260908001");

        String expectedSource = "merchantOrderNo=F2F20260908001";
        assertEquals(expectedSource, PayCenterSigner.buildSignSource(decodeBizData(request)));
        assertFalse(expectedSource.contains("merchantNo"), "信封字段 NEVER 进待签串");

        Signature verifier = Signature.getInstance("SHA256WithRSA");
        verifier.initVerify(publicKey);
        verifier.update(expectedSource.getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(request.getSign())));
    }

    /** 键名按字典序排、空白值剔掉——与 {@code PayGatewayClient.buildSignSource} 同规则。 */
    @Test
    void signSourceSortsKeysAndSkipsBlankValues() {
        String source = PayCenterSigner.buildSignSource(
                "{\"subject\":\"地铁\",\"amount\":600,\"remark\":\"\",\"orderNo\":\"F2F001\"}");

        assertEquals("amount=600&orderNo=F2F001&subject=地铁", source);
    }

    @Test
    void aggregateCodeUrlIsBuiltLocallyWithMd5Sign() {
        String url = factory.buildAggregateCodePayUrl("F2F20260908001");

        assertTrue(url.startsWith("http://pay.example/api/v1/checkoutCounter?orderNo=F2F20260908001&sign="));
        String sign = url.substring(url.indexOf("&sign=") + 6);
        assertEquals(32, sign.length(), "MD5 十六进制应为 32 位");
        assertEquals(sign.toLowerCase(), sign, "旧实现用小写十六进制");
    }

    @Test
    void scanWithoutAuthCodeIsRejectedAtCommandLevel() {
        assertThrows(IllegalArgumentException.class, () -> new PayCenterPayCommand(
                "F2F20260908004", PayScene.SCAN, "0A", null, 100L, "s", "b", null));
    }

    @Test
    void nonPositiveAmountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PayCenterPayCommand(
                "F2F20260908005", PayScene.QRCODE, null, "0", 0L, "s", "b", null));
    }

    @Test
    void unknownGatewayStatusIsNotFailed() {
        assertEquals(null, PayCenterStatus.fromCode("WHATEVER"));
        assertEquals(PayCenterStatus.ORDERED, PayCenterStatus.fromCode("ORDERED"));
    }

    private static String decodeBizData(PayCenterRequest request) {
        return new String(Base64.getDecoder().decode(request.getBizData()), StandardCharsets.UTF_8);
    }
}
