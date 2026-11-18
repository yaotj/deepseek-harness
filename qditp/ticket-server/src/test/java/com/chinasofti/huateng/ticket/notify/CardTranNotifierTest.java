package com.chinasofti.huateng.ticket.notify;

import com.chinasofti.huateng.model.app.AppCardTranNotifyReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardTranNotifierTest {

    private final CardTranNotifier notifier = new CardTranNotifier(null, null);

    @Test
    void 表125字段逐项映射且保留规格里的tikcetTransSeq拼写() {
        NotifyVerifyResultReqDTO request = request();
        QRCodeStatus status = new QRCodeStatus();
        status.setCodeStatus("05");

        AppCardTranNotifyReqDTO dto = notifier.buildNotifyRequest(request, status, "Y");

        assertEquals("00522986", dto.getThirdUserId());
        assertEquals("02", dto.getTrxType());
        assertEquals("05", dto.getTicketStatus(), "ticketStatus 取推进后的票卡状态");
        assertEquals("07", dto.getSignChannelCode());
        assertEquals("C001", dto.getCardId());
        assertEquals("0441", dto.getCardType());
        assertEquals("20261117120000", dto.getHandleDateTime());
        assertEquals("0101", dto.getHandleStationCode());
        assertEquals("350", dto.getTrxAmount());
        assertEquals("0", dto.getOvertimeAmount());
        assertEquals("04", dto.getLastTicketStatus());
        assertEquals("0102", dto.getLastHandleStationCode());
        assertEquals("20261117110000", dto.getLastHandleDateTime());
        assertEquals("9", dto.getTikcetTransSeq());
        assertEquals("AGM01", dto.getDeviceId());
        assertEquals("00", dto.getChannelType());
        assertEquals("Y", dto.getCompanionFlag());
        assertEquals("Y", dto.getCountingFlag());
    }

    @Test
    void 支付渠道编码优先用账户域paymentVendor() {
        NotifyVerifyResultReqDTO request = request();
        request.setPaymentVendor("0x03");
        request.setSignChannelCode("01");

        assertEquals("03", notifier.buildNotifyRequest(request, new QRCodeStatus(), "N").getSignChannelCode());
    }

    @Test
    void 缺channelType时按闸机00兜底() {
        NotifyVerifyResultReqDTO request = request();
        request.setChannelType(null);

        assertEquals("00", notifier.buildNotifyRequest(request, new QRCodeStatus(), "N").getChannelType());
    }

    @ParameterizedTest(name = "httpSuccessful={0}, body={1} -> {2}")
    @CsvSource(nullValues = "NULL", value = {
            "true,  '{\"retCode\":\"0000\"}',                          true",
            "true,  '{\"retCode\":\"0000\",\"retMsg\":\"成功\"}',        true",
            "true,  '{\"retCode\":\"7004\",\"retMsg\":\"处理过程出现错误!\"}', false",
            "true,  '{\"retMsg\":\"成功\"}',                            false",
            "true,  '',                                                false",
            "true,  NULL,                                              false",
            "true,  这不是JSON,                                         false",
            "false, '{\"retCode\":\"0000\"}',                          false",
    })
    void 卡片行程通知MUST同时判HTTP2xx和retCode(boolean httpSuccessful, String responseBody, boolean expected) {
        assertEquals(expected, notifier.isAccepted(httpSuccessful, responseBody));
    }

    @Test
    void 非0000业务码不算受理() {
        assertFalse(notifier.isAccepted(true, "{\"retCode\":\"7004\"}"));
        assertTrue(notifier.isAccepted(true, "{\"retCode\":\"0000\"}"));
    }

    private NotifyVerifyResultReqDTO request() {
        NotifyVerifyResultReqDTO request = new NotifyVerifyResultReqDTO();
        request.setItpUserId("00522986");
        request.setTrxType("02");
        request.setPaymentVendor("07");
        request.setSignChannelCode("01");
        request.setCardId("C001");
        request.setCardType("0441");
        request.setHandleDateTime("20261117120000");
        request.setHandleStationCode("0101");
        request.setTrxAmount("350");
        request.setOvertimeAmount("0");
        request.setLastTicketStatus("04");
        request.setLastHandleStationCode("0102");
        request.setLastHandleDateTime("20261117110000");
        request.setTicketTransSeq("9");
        request.setDeviceId("AGM01");
        request.setChannelType("00");
        request.setCompanionFlag("Y");
        return request;
    }
}
