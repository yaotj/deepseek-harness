package com.chinasofti.huateng.paysign.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.chinasofti.huateng.model.app.ItpCommonRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppNotifySignerTest {

    private static final String KEY = "TESTKEY";

    private static final String EXPECTED_SHA1 = "4abecf5345e6841c3f426529ab14ac84b2f67922";

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

    @Test
    void signTypeZeroZeroMeansNoSignature() {
        assertNull(AppNotifySigner.buildItpSign(request("00"), KEY));
    }

    @Test
    void unknownSignTypeReturnsNull() {
        assertNull(AppNotifySigner.buildItpSign(request("99"), KEY));
    }

    @Test
    void blankSignTypeMeansNoSignature() {
        assertNull(AppNotifySigner.buildItpSign(request(null), KEY));
    }

    @Test
    void blankKeyStillProducesSignature() {
        String sign = AppNotifySigner.buildItpSign(request("01"), "");
        assertEquals(40, sign.length());
    }
}
