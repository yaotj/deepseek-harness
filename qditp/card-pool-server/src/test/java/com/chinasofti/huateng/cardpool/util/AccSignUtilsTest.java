package com.chinasofti.huateng.cardpool.util;

import com.chinasofti.huateng.cardpool.config.AccSecureProperties;
import com.chinasofti.huateng.model.accsecure.RequestQrLogicNumListReqDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** ACC 签名迁移的回归断言。 */
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

    /** signType=00 不签名，返回空串。 */
    @Test
    void signTypeNoneReturnsEmpty() {
        String sign = AccSignUtils.buildSign(PROVIDER_ID, CHARSET, FORMAT, TIMESTAMP, DEVICE_ID,
                "00", bizData(), properties(""));
        assertEquals("", sign);
    }

    /** signType 为空按不签名处理，与迁出侧一致。 */
    @Test
    void blankSignTypeReturnsEmpty() {
        String sign = AccSignUtils.buildSign(PROVIDER_ID, CHARSET, FORMAT, TIMESTAMP, DEVICE_ID,
                null, bizData(), properties(""));
        assertEquals("", sign);
    }

    /** signType=02 但未配 signKey 时拒绝出签，避免退化成无密钥 MD5。 */
    @Test
    void signTypeMd5WithoutKeyRejected() {
        assertThrows(IllegalStateException.class, () -> AccSignUtils.buildSign(
                PROVIDER_ID, CHARSET, FORMAT, TIMESTAMP, DEVICE_ID, "02", bizData(), properties("")));
    }

    /** signType=02 且配了 signKey 时追加 {@code &key=} 后的 MD5 与迁出侧一致。 */
    @Test
    void signTypeMd5WithKeyMatchesLegacy() {
        String sign = AccSignUtils.buildSign(PROVIDER_ID, CHARSET, FORMAT, TIMESTAMP, DEVICE_ID,
                "02", bizData(), properties("TESTKEY"));
        assertEquals("19f19bc1d8dc172e9527260801d8e30c", sign);
    }

    /** 除 00 / 02 外的 signType 一律拒绝，与迁出侧一致。 */
    @Test
    void unsupportedSignTypeRejected() {
        AccSecureProperties properties = properties("");
        RequestQrLogicNumListReqDTO biz = bizData();
        assertThrows(IllegalArgumentException.class, () -> AccSignUtils.buildSign(
                PROVIDER_ID, CHARSET, FORMAT, TIMESTAMP, DEVICE_ID, "01", biz, properties));
    }
}
