package com.chinasofti.huateng.cardpool.util;

import com.chinasofti.huateng.cardpool.config.AccSecureProperties;
import com.chinasofti.huateng.model.accsecure.RequestQrLogicNumListReqDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccSignUtilsTest {

    private static final String TIMESTAMP = "20260909120000";
    private static final String PROVIDER_ID = "06";
    private static final String CHARSET = "UTF-8";
    private static final String FORMAT = "json";
    private static final String DEVICE_ID = "ITP-CARD-POOL";

    private RequestQrLogicNumListReqDTO bizData() {
        RequestQrLogicNumListReqDTO biz = new RequestQrLogicNumListReqDTO();
        biz.setRequestNum("100000");
        biz.setRequestSeq("100031");
        biz.setTicketType("41");
        return biz;
    }

    private AccSecureProperties properties(String signKey) {
        AccSecureProperties properties = new AccSecureProperties();
        properties.setSignKey(signKey);
        return properties;
    }

    @Test
    void signTypeNoneReturnsEmpty() {
        String sign = AccSignUtils.buildSign(PROVIDER_ID, CHARSET, FORMAT, TIMESTAMP, DEVICE_ID,
                "00", bizData(), properties(""));
        assertEquals("", sign);
    }

    @Test
    void blankSignTypeReturnsEmpty() {
        String sign = AccSignUtils.buildSign(PROVIDER_ID, CHARSET, FORMAT, TIMESTAMP, DEVICE_ID,
                null, bizData(), properties(""));
        assertEquals("", sign);
    }

    @Test
    void signTypeMd5WithoutKeyRejected() {
        assertThrows(IllegalStateException.class, () -> AccSignUtils.buildSign(
                PROVIDER_ID, CHARSET, FORMAT, TIMESTAMP, DEVICE_ID, "02", bizData(), properties("")));
    }

    @Test
    void signTypeMd5WithKeyMatchesLegacy() {
        String sign = AccSignUtils.buildSign(PROVIDER_ID, CHARSET, FORMAT, TIMESTAMP, DEVICE_ID,
                "02", bizData(), properties("TESTKEY"));
        assertEquals("19f19bc1d8dc172e9527260801d8e30c", sign);
    }

    @Test
    void unsupportedSignTypeRejected() {
        AccSecureProperties properties = properties("");
        RequestQrLogicNumListReqDTO biz = bizData();
        assertThrows(IllegalArgumentException.class, () -> AccSignUtils.buildSign(
                PROVIDER_ID, CHARSET, FORMAT, TIMESTAMP, DEVICE_ID, "01", biz, properties));
    }
}
