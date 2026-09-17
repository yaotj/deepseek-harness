package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayLogMapper;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 钉住 IF8A 支付宝出行「查询乘车记录详情」的 debitRequestResult 值域。 */
class TravelDetailDebitResultTest {

    private static final String ORDER_NO = "AL20260914000000000000001";

    private AlipayPayLogMapper mapper;
    private PaymentQueryService service;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        mapper = mock(AlipayPayLogMapper.class);
        service = new PaymentQueryService();
        Field field = PaymentQueryService.class.getDeclaredField("alipayPayLogMapper");
        field.setAccessible(true);
        field.set(service, mapper);
    }

    @Test
    void successPayStatusIsMappedToZero() {
        assertEquals("0", debitResultOf("SUCCESS", "交易成功"),
                "扣费成功 MUST 返 \"0\"");
    }

    @Test
    void failPayStatusIsMappedToOne() {
        assertEquals("1", debitResultOf("FAIL", "余额不足"),
                "扣费失败 MUST 返 \"1\"");
    }

    @Test
    void processingPayStatusIsMappedToOne() {
        assertEquals("1", debitResultOf("PROCESSING", "处理中"),
                "未收口的单 MUST 返 \"1\"，NEVER 提前报成功");
    }

    @Test
    void nullPayStatusIsMappedToOne() {
        assertEquals("1", debitResultOf(null, null),
                "状态缺失 MUST 保守返 \"1\"，且 NEVER NPE");
    }

    @Test
    void resultMsgNeverLeaksIntoTheContractField() {
        // 上线前该字段填的就是 RESULT_MSG，支付宝按 0/1 解析时成功单也显示扣费未成功。
        assertEquals("0", debitResultOf("SUCCESS", "交易成功"),
                "RESULT_MSG 的文案 MUST NOT 出现在 debitRequestResult 里");
    }

    @Test
    void payStatusMatchIsCaseInsensitive() {
        assertEquals("0", debitResultOf("success", "交易成功"),
                "与 fep-alipay-server 的 mapPayStatusToDebitResult 一致，MUST 忽略大小写");
    }

    private String debitResultOf(String payStatus, String resultMsg) {
        AlipayPayLog payLog = new AlipayPayLog();
        payLog.setOrderNo(ORDER_NO);
        payLog.setPayStatus(payStatus);
        payLog.setResultMsg(resultMsg);
        payLog.setPayAmount("400");
        when(mapper.selectByOrderNo(anyString())).thenReturn(payLog);

        AlipayTripFindTravelDetailReqDTO request = new AlipayTripFindTravelDetailReqDTO();
        request.setOrderNo(ORDER_NO);
        AlipayTripFindTravelDetailRespDTO response = service.findTravelDetail(request);
        return response.getDebitRequestResult();
    }
}
