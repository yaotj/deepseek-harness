package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.alipay.paysign.service.impl.payment.PaymentQueryService;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayLogMapper;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void failPayStatusIsMappedToTwo() {
        assertEquals("2", debitResultOf("FAIL", "余额不足"),
                "IT-082：扣费失败 MUST 返 \"2\"，NEVER 用 \"1\"（旧语义是线下补款人工成功）");
    }

    @Test
    void processingPayStatusLeavesResultEmpty() {
        assertNull(debitResultOf("PROCESSING", "处理中"),
                "IT-082 阶段三：在途 MUST 返 null（APP 按「空 = 支付中」渲染），NEVER 提前报失败");
    }

    @Test
    void retryAndInitPayStatusAlsoLeaveResultEmpty() {
        assertNull(debitResultOf("RETRY", null), "补偿重试中的单没有定论，MUST 按支付中");
        assertNull(debitResultOf("INIT", null), "已落单未发起扣款 MUST 按支付中");
    }

    @Test
    void nullPayStatusIsMappedToTwo() {
        assertEquals("2", debitResultOf(null, null),
                "IT-082：状态缺失属脏数据 MUST 保守返 \"2\"，NEVER 当成支付中永久转圈");
    }

    @Test
    void IT082对外扣款结果只产出0空2且永不产出1和3() {
        for (String status : new String[]{"SUCCESS", "FAIL", "PROCESSING", "RETRY", "INIT", "CLOSED", null, "", "UNKNOWN"}) {
            String result = debitResultOf(status, "任意文案");
            assertTrue(result == null || "0".equals(result) || "2".equals(result),
                    () -> "payStatus=" + status + " 时只允许 0 / 空 / 2，实得 " + result);
        }
    }

    @Test
    void resultMsgNeverLeaksIntoTheContractField() {
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
        return response.getData().getDebitRequestResult();
    }
}
