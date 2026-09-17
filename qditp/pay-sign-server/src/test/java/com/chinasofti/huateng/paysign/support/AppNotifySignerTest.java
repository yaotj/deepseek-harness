package com.chinasofti.huateng.paysign.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.chinasofti.huateng.model.app.ItpCommonRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 护栏：出向加签的源串口径与摘要，期望值独立计算、NEVER 拿实现输出回填。 */
class AppNotifySignerTest {

    private static final String KEY = "TESTKEY";

    /** signType=01 ⇒ SHA-1，期望值由外部独立计算（源串见下）。 */
    private static final String EXPECTED_SHA1 = "4abecf5345e6841c3f426529ab14ac84b2f67922";

    /** signType=02 ⇒ MD5，同一源串（signType 段换成 02）。 */
    private static final String EXPECTED_MD5 = "23fb02f1e9dd671c589cd768e61f5b56";

    private ItpCommonRequest<Map<String, String>> request(String signType) {
        ItpCommonRequest<Map<String, String>> request = new ItpCommonRequest<>();
        request.setProviderId("06");
        request.setCharset("UTF-8");
        request.setFormat("json");
        request.setTimestamp("20260916101112");
        request.setDeviceId("ITP-PAY-SIGN");
        request.setSignType(signType);
        Map<String, String> bizData = new LinkedHashMap<>();
        bizData.put("b", "2");
        bizData.put("a", "1");
        request.setBizData(bizData);
        return request;
    }

    @Test
    void signTypeSha1MatchesIndependentlyComputedDigest() {
        assertEquals(EXPECTED_SHA1, AppNotifySigner.buildItpSign(request("01"), KEY));
    }

    @Test
    void signTypeMd5MatchesIndependentlyComputedDigest() {
        assertEquals(EXPECTED_MD5, AppNotifySigner.buildItpSign(request("02"), KEY));
    }

    /** {@code signType=00} 是「免签」，MUST 返回 null 而不是空串或某个摘要。 */
    @Test
    void signTypeZeroZeroMeansNoSignature() {
        assertNull(AppNotifySigner.buildItpSign(request("00"), KEY));
    }

    /** 未约定的 signType MUST 返回 null，NEVER 退化成默认用某个算法。 */
    @Test
    void unknownSignTypeReturnsNull() {
        assertNull(AppNotifySigner.buildItpSign(request("99"), KEY));
    }

    /** 空 signType 同样不签；这条守的是「没配 signType 时不要凭空造一个 sign」。 */
    @Test
    void blankSignTypeMeansNoSignature() {
        assertNull(AppNotifySigner.buildItpSign(request(null), KEY));
    }

    /** 密钥为空时**仍然出签**（源串末尾是 {@code &key=}），这是搬迁前的原样行为。 */
    @Test
    void blankKeyStillProducesSignature() {
        String sign = AppNotifySigner.buildItpSign(request("01"), "");
        assertEquals(40, sign.length());
    }
}
