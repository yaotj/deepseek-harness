package com.chinasofti.huateng.facepay.controller.paycenter;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SupplementPayNoticeControllerTest {

    private static final String SUPP_ORDER_NO = "SP20261120170624409000126";

    private static final String PAY_CENTER_ORDER_NO = "294051073527545856";

    /** 支付中心真实回调的 bizData（解码后），merchantOrderNo=补款单号，orderNo=支付中心单号。 */
    private static final String REAL_BIZ_DATA_JSON = "{\"cashAmount\":3829,\"channelOrderNo\":\"\","
            + "\"couponAmount\":0,\"merchantOrderNo\":\"" + SUPP_ORDER_NO + "\","
            + "\"orderNo\":\"" + PAY_CENTER_ORDER_NO + "\",\"payTime\":\"20261120171258\","
            + "\"payUserId\":\"\",\"paymentVendor\":\"04\",\"status\":\"SUCCESS\",\"totalAmount\":3829}";

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> realCallback() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("apiVersion", "1.0");
        body.put("bizData", base64(REAL_BIZ_DATA_JSON));
        body.put("charset", "UTF-8");
        body.put("merchantNo", "JOPJ490HLK9Z");
        body.put("sign", "dummy");
        body.put("signType", "RSA2");
        return body;
    }

    @Test
    void extractsSupplementOrderNoFromRealBase64Callback() {
        assertEquals(SUPP_ORDER_NO, SupplementPayNoticeController.extractOrderNo(realCallback()),
                "真实回调的订单号在 Base64 bizData 的 merchantOrderNo 里，必须能提出补款单号，否则回调被吞、状态靠 5 分钟兜底");
    }

    @Test
    void neverUsesPayCenterOrderNoFromBizData() {
        // bizData 里同时有 orderNo（支付中心单号）与 merchantOrderNo（补款单号），
        // 只取后者；误取前者会让 handlePayNotice 查不到补款单、即时收敛失效。
        String onlyPayCenterOrder = "{\"orderNo\":\"" + PAY_CENTER_ORDER_NO + "\",\"status\":\"SUCCESS\"}";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bizData", base64(onlyPayCenterOrder));
        assertNull(SupplementPayNoticeController.extractOrderNo(body),
                "bizData.orderNo 是支付中心单号，NEVER 当补款单号用；没有 merchantOrderNo 就该返回 null");
    }

    @Test
    void acceptsPlainJsonBizData() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bizData", REAL_BIZ_DATA_JSON);
        assertEquals(SUPP_ORDER_NO, SupplementPayNoticeController.extractOrderNo(body),
                "bizData 为 JSON 明文时也 MUST 能提取");
    }

    @Test
    void acceptsBizDataAsAlreadyParsedMap() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("merchantOrderNo", SUPP_ORDER_NO);
        inner.put("orderNo", PAY_CENTER_ORDER_NO);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bizData", inner);
        assertEquals(SUPP_ORDER_NO, SupplementPayNoticeController.extractOrderNo(body),
                "bizData 已被解析成 Map 时 MUST 能提取");
    }

    @Test
    void keepsLegacyTopLevelFields() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantOrderNo", SUPP_ORDER_NO);
        body.put("orderNo", PAY_CENTER_ORDER_NO);
        assertEquals(SUPP_ORDER_NO, SupplementPayNoticeController.extractOrderNo(body),
                "顶层 merchantOrderNo 优先于顶层 orderNo，兼容早期扁平报文");

        Map<String, Object> onlyTopOrder = new LinkedHashMap<>();
        onlyTopOrder.put("orderNo", SUPP_ORDER_NO);
        assertEquals(SUPP_ORDER_NO, SupplementPayNoticeController.extractOrderNo(onlyTopOrder),
                "顶层只有 orderNo 且是补款单号时（早期报文）仍按原样兼容");
    }

    @Test
    void returnsNullForMissingOrMalformedBizData() {
        assertNull(SupplementPayNoticeController.extractOrderNo(null));
        assertNull(SupplementPayNoticeController.extractOrderNo(Map.of()));

        Map<String, Object> notBase64 = new LinkedHashMap<>();
        notBase64.put("bizData", "not-base64-not-json");
        assertNull(SupplementPayNoticeController.extractOrderNo(notBase64),
                "bizData 既非 JSON 又非 Base64 时 MUST 返回 null，NEVER 抛异常打断回调");

        Map<String, Object> jsonNoMerchant = new LinkedHashMap<>();
        jsonNoMerchant.put("bizData", base64("{\"status\":\"SUCCESS\"}"));
        assertNull(SupplementPayNoticeController.extractOrderNo(jsonNoMerchant));
    }
}
