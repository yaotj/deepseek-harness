package com.chinasofti.huateng.dailyticket.service.paylog;

import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayResponse;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketPayLogMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketPayLog;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 钉住支付日志的应答码抽取分支：三种 response 形态各走一支，写错会让对账与排查取不到 retCode。
 */
class DailyTicketPayLogWriterTest {

    private DailyTicketPayLogMapper payLogMapper;
    private DailyTicketPayLogWriter writer;

    @BeforeEach
    void setUp() {
        payLogMapper = mock(DailyTicketPayLogMapper.class);
        writer = new DailyTicketPayLogWriter(payLogMapper);
    }

    private DailyTicketPayLog captureInserted() {
        ArgumentCaptor<DailyTicketPayLog> captor = ArgumentCaptor.forClass(DailyTicketPayLog.class);
        verify(payLogMapper).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    void baseResultResponseFillsRetCodeAndMsg() {
        DailyTicketBaseResult response = new DailyTicketBaseResult();
        response.setRetCode("0000");
        response.setRetMsg("成功");

        writer.insert("0E01", "REFUND_CALLBACK", "ALIPAY", Map.of("k", "v"), response);

        DailyTicketPayLog saved = captureInserted();
        assertEquals("0E01", saved.getOrderNo());
        assertEquals("REFUND_CALLBACK", saved.getBizType());
        assertEquals("ALIPAY", saved.getPayChannelCode());
        assertEquals("0000", saved.getResultCode());
        assertEquals("成功", saved.getResultMsg());
        assertNotNull(saved.getCreateTime());
    }

    @Test
    void gatewayResponseFillsCodeAsString() {
        DailyTicketPayGatewayResponse response = new DailyTicketPayGatewayResponse();
        response.setCode(9999);
        response.setMsg("操作失败");

        writer.insert("0E02", "REFUND", "WECHAT", new LinkedHashMap<>(), response);

        DailyTicketPayLog saved = captureInserted();
        assertEquals("9999", saved.getResultCode());
        assertEquals("操作失败", saved.getResultMsg());
    }

    @Test
    void gatewayResponseWithNullCodeKeepsResultCodeNull() {
        DailyTicketPayGatewayResponse response = new DailyTicketPayGatewayResponse();
        response.setMsg("无应答码");

        writer.insert("0E03", "PAY", null, new LinkedHashMap<>(), response);

        DailyTicketPayLog saved = captureInserted();
        assertNull(saved.getResultCode());
        assertEquals("无应答码", saved.getResultMsg());
    }

    @Test
    void unknownResponseShapeStillPersistsBodies() {
        writer.insert("0E04", "PAY_QUERY", "ALIPAY", Map.of("req", 1), Map.of("resp", 2));

        DailyTicketPayLog saved = captureInserted();
        assertNull(saved.getResultCode());
        assertNull(saved.getResultMsg());
        assertTrue(saved.getRequestBody().contains("req"));
        assertTrue(saved.getResponseBody().contains("resp"));
    }

    @Test
    void idIsThirtyTwoCharHexWithoutHyphen() {
        writer.insert("0E05", "PAY", "ALIPAY", null, null);

        String id = captureInserted().getId();
        assertEquals(32, id.length());
        assertTrue(id.matches("[0-9a-f]{32}"));
    }
}
