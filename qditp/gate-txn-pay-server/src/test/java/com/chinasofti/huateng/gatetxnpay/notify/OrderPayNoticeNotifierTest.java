package com.chinasofti.huateng.gatetxnpay.notify;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.model.app.AppOrderPayNoticeReqDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R6 §3.62 行程订单通知：钉住表127 的 16 个字段映射与受理判据，防止字段错位或口径漂移。
 */
class OrderPayNoticeNotifierTest {

    @Test
    void table127FieldsMapFromSettledOrder() {
        AppOrderPayNoticeReqDTO request = notifier().buildNotifyRequest(order(), "SUCCESS");

        assertEquals("00522980", request.getThirdUserId(), "thirdUserId MUST 取 THIRD_USER_ID");
        assertEquals("双珠路", request.getEntryStationName());
        assertEquals("仙家寨(汽车北站)", request.getExitStationName());
        assertEquals("20261120110536", request.getEntryStationDate(), "站时间 MUST 是 14 位 yyyyMMddHHmmss（APP 要求）");
        assertEquals("20261120110546", request.getExitStationDate(), "站时间 MUST 是 14 位 yyyyMMddHHmmss（APP 要求）");
        assertEquals("800", request.getPayAmount(), "payAmount MUST 是订单应付总额（分）");
        assertEquals("GT20261120110545409000081", request.getTradeOrderNo(), "交易订单号 MUST 是我方过闸订单号");
        assertEquals("GT20261120110545409000081", request.getPayTradeOrderNo(), "支付单号缺失时按订单号兜底");
        assertEquals("0", request.getOrderExpType());
        assertEquals("03", request.getPayChannelCode(), "支付渠道 MUST 取 paymentVendor");
        assertEquals("0", request.getOrderStatus(), "SUCCESS 对外口径 0");
        assertEquals("GT20261120110545409000081", request.getRequestDebitNo(), "扣款请求号缺失时按订单号兜底");
        assertEquals("0426090949000081", request.getCardNo());
        assertEquals("0441", request.getTicketType());
        assertEquals("43997", request.getTransSeq());
        assertEquals("800", request.getDebitAmount(), "成功单实扣 = 应付总额");
    }

    @Test
    void failedOrderReportsZeroDebitAmountButKeepsPayAmount() {
        AppOrderPayNoticeReqDTO request = notifier().buildNotifyRequest(order(), "FAIL");

        assertEquals("2", request.getOrderStatus(), "FAIL 对外口径 2");
        assertEquals("800", request.getPayAmount(), "失败也要让 APP 看到应付金额");
        assertEquals("0", request.getDebitAmount(), "失败单实扣 MUST 为 0");
    }

    @Test
    void missingStationNameFallsBackToStationCode() {
        GateTxnPay order = order();
        order.setEntryStationName(null);

        AppOrderPayNoticeReqDTO request = notifier().buildNotifyRequest(order, "SUCCESS");

        assertEquals("1332", request.getEntryStationName(), "站名缺失 MUST 按站码兜底，不推空串");
    }

    @Test
    void stationTimeIsNormalizedTo14Digits() {
        GateTxnPay order = order();
        order.setInTime("20261120");
        order.setOutTime("2026-11-20 11:05:46");

        AppOrderPayNoticeReqDTO request = notifier().buildNotifyRequest(order, "SUCCESS");

        assertEquals("20261120000000", request.getEntryStationDate(), "8 位日期补零点，仍是 14 位");
        assertEquals("20261120110546", request.getExitStationDate(), "带横线时间转成紧凑 14 位");
    }

    @Test
    void blankStationTimeStaysBlank() {
        GateTxnPay order = order();
        order.setOutTime("");

        AppOrderPayNoticeReqDTO request = notifier().buildNotifyRequest(order, "SUCCESS");

        assertEquals("", request.getExitStationDate(), "缺失时间留空，不臆造时间");
    }

    @Test
    void acceptedOnlyWhenHttpOkAndRetCode0000() {
        OrderPayNoticeNotifier notifier = notifier();

        assertTrue(notifier.isAccepted("{\"retCode\":\"0000\",\"retMsg\":\"成功\"}"));
        assertFalse(notifier.isAccepted("{\"retCode\":\"7004\",\"retMsg\":\"查询无记录\"}"));
        assertFalse(notifier.isAccepted("{\"retCode\":\"9999\",\"retMsg\":\"验证签名失败\"}"));
        assertFalse(notifier.isAccepted("not-json"));
        assertFalse(notifier.isAccepted(""));
    }

    @Test
    void formCarriesCommonParametersAndBizData() {
        MultipartBodyBuilder builder = notifier().buildForm("{\"thirdUserId\":\"00522980\"}", "01540508");
        String[] names = builder.build().keySet().toArray(new String[0]);

        assertEquals(8, names.length, "公共参数 7 项 + bizData，与卡片行程通知同形");
        assertTrue(builder.build().containsKey("bizData"));
        assertTrue(builder.build().containsKey("providerId"));
        assertTrue(builder.build().containsKey("signType"));
        assertTrue(builder.build().containsKey("deviceId"));
    }

    private OrderPayNoticeNotifier notifier() {
        return new OrderPayNoticeNotifier(
                "http://127.0.0.1:9/app/orderPayNotice", true, false, 1000L,
                "01", "UTF-8", "json", "00", "", WebClient.builder());
    }

    private GateTxnPay order() {
        GateTxnPay order = new GateTxnPay();
        order.setOrderNo("GT20261120110545409000081");
        order.setThirdUserId("00522980");
        order.setCardId("0426090949000081");
        order.setCardType("0441");
        order.setDeviceId("01540508");
        order.setTicketTransSeq("43997");
        order.setInStation("1332");
        order.setOutStation("0154");
        order.setInTime("20261120110536");
        order.setOutTime("20261120110546");
        order.setTxnDate("20261120");
        order.setEntryStationName("双珠路");
        order.setExitStationName("仙家寨(汽车北站)");
        order.setTrxAmount(800);
        order.setOvertimeAmount(0);
        order.setTotalAmount(800);
        order.setOrderExpType("0");
        order.setPaymentVendor("03");
        order.setSignChannelCode("03");
        return order;
    }
}
