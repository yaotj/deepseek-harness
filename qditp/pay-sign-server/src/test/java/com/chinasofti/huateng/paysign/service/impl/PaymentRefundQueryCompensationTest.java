package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.paysign.client.PayGatewayClient;
import com.chinasofti.huateng.paysign.config.PaySignProperties;
import com.chinasofti.huateng.paysign.entity.PayRefundDetail;
import com.chinasofti.huateng.paysign.mapper.PayRefundDetailMapper;
import com.chinasofti.huateng.paysign.mapper.PayTxnDetailMapper;
import com.chinasofti.huateng.paysign.port.RefundGatewayAdapter;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.support.PaySignGateway;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 护栏：退款回查收口 CAS 命中 0 行不当成功、真收口必重算汇总、非终态只推退避。 */
class PaymentRefundQueryCompensationTest {

    private static final String REFUND_QUERY_URL = "http://pay-gateway.test/api/v1/refund/refundQuery";

    private final PaySignProperties properties = new PaySignProperties();
    private final PayRefundDetailMapper payRefundDetailMapper = mock(PayRefundDetailMapper.class);
    private final PayTxnDetailMapper payTxnDetailMapper = mock(PayTxnDetailMapper.class);
    private final PayGatewayClient payGatewayClient = mock(PayGatewayClient.class);
    private final PaySignGateway paySignGateway = new PaySignGateway(payGatewayClient);

    private RefundDomainServiceImpl newService() {
        properties.setRefundQueryUrl(REFUND_QUERY_URL);
        when(payGatewayClient.isSuccess(any())).thenCallRealMethod();
        RefundDomainServiceImpl service = new RefundDomainServiceImpl(
                payTxnDetailMapper,
                payRefundDetailMapper,
                new RefundGatewayAdapter(properties, paySignGateway));
        return service;
    }

    private PayRefundDetail processingRow() {
        PayRefundDetail row = new PayRefundDetail();
        row.setRefundOrderNo("RF20260915000000001");
        row.setTxnDate("20260915");
        row.setOrderNo("GT20260915000000001");
        row.setRefundStatus("PROCESSING");
        return row;
    }

    private void gatewayReturns(String status) {
        PaySignGatewayResponse response = new PaySignGatewayResponse();
        response.setCode(0);
        response.setMsg("成功");
        Map<String, Object> data = new HashMap<>();
        if (status != null) {
            data.put("status", status);
        }
        data.put("refundNo", "PC123456");
        response.setData(data);
        when(payGatewayClient.request(anyString(), anyMap())).thenReturn(response);
    }

    /** 收口 CAS 命中 0 行：NEVER 当成功，且 NEVER 去重算汇总。 */
    @Test
    void casMissNeverCountsAsSettledAndNeverTouchesSummary() {
        RefundDomainServiceImpl service = newService();
        when(payRefundDetailMapper.selectCompensableRefundQuery(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(processingRow()));
        gatewayReturns("SUCCESS");
        when(payRefundDetailMapper.finishFromQuery(any(PayRefundDetail.class))).thenReturn(0);

        CompensateNotifyRespDTO response = service.compensateRefundQuery();

        assertEquals(1, response.getScanned(), "扫到 1 条");
        assertEquals(0, response.getSubmitted(),
                "CAS 命中 0 行 MUST NOT 计入已收口 —— 那一行已被别的路径收口，本轮什么都没推动");
        assertEquals(1, response.getSkipped(), "MUST 计入本轮未收口，留给下一轮或人工");
        verify(payTxnDetailMapper, never()).updateRefundSummary(anyString());
    }

    /** 收口 CAS 命中 1 行：MUST 无条件重算一次汇总（这是摘事务后唯一的补偿出口）。 */
    @Test
    void settledRowAlwaysRecomputesRefundSummary() {
        RefundDomainServiceImpl service = newService();
        when(payRefundDetailMapper.selectCompensableRefundQuery(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(processingRow()));
        gatewayReturns("SUCCESS");
        when(payRefundDetailMapper.finishFromQuery(any(PayRefundDetail.class))).thenReturn(1);
        when(payTxnDetailMapper.updateRefundSummary(anyString())).thenReturn(1);

        CompensateNotifyRespDTO response = service.compensateRefundQuery();

        assertEquals(1, response.getSubmitted(), "真收口的行 MUST 计入 submitted");
        assertEquals(0, response.getSkipped(), "没有失败行");
        verify(payTxnDetailMapper, times(1)).updateRefundSummary("GT20260915000000001");
    }

    /** 支付中心仍答处理中：MUST 只推退避时间，NEVER 落终态。 */
    @Test
    void nonTerminalQueryOnlyDelaysNextAttempt() {
        RefundDomainServiceImpl service = newService();
        when(payRefundDetailMapper.selectCompensableRefundQuery(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(processingRow()));
        gatewayReturns("REFUNDING");
        when(payRefundDetailMapper.delayNextRefundQuery(anyString(), anyString(), anyInt())).thenReturn(1);

        CompensateNotifyRespDTO response = service.compensateRefundQuery();

        assertEquals(1, response.getSkipped(), "未得终态 MUST 计入本轮未收口");
        verify(payRefundDetailMapper, never()).finishFromQuery(any(PayRefundDetail.class));
        verify(payRefundDetailMapper, times(1))
                .delayNextRefundQuery(eq("RF20260915000000001"), eq("20260915"), anyInt());
        verify(payTxnDetailMapper, never()).updateRefundSummary(anyString());
    }

    /** 退避 CAS 也命中 0 行：仍计入 skipped，且 NEVER 落终态（2026-09-16，ADR-D115 续）。 */
    @Test
    void delayCasMissStillCountsAsSkippedAndWritesNothing() {
        RefundDomainServiceImpl service = newService();
        when(payRefundDetailMapper.selectCompensableRefundQuery(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(processingRow()));
        gatewayReturns("REFUNDING");
        when(payRefundDetailMapper.delayNextRefundQuery(anyString(), anyString(), anyInt())).thenReturn(0);

        CompensateNotifyRespDTO response = service.compensateRefundQuery();

        assertEquals(1, response.getSkipped(), "退避 CAS 落空 MUST 仍计入本轮未收口");
        assertEquals(0, response.getSubmitted());
        verify(payRefundDetailMapper, never()).finishFromQuery(any(PayRefundDetail.class));
        verify(payTxnDetailMapper, never()).updateRefundSummary(anyString());
    }

    /** 单条抛异常 NEVER 中断整批：另一条照样收口，异常那条计入 failed。 */
    @Test
    void singleRowFailureNeverAbortsTheWholeBatch() {
        RefundDomainServiceImpl service = newService();
        PayRefundDetail bad = processingRow();
        PayRefundDetail good = processingRow();
        good.setRefundOrderNo("RF20260915000000002");
        good.setOrderNo("GT20260915000000002");
        when(payRefundDetailMapper.selectCompensableRefundQuery(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(bad, good));
        gatewayReturns("SUCCESS");
        when(payRefundDetailMapper.finishFromQuery(any(PayRefundDetail.class)))
                .thenThrow(new RuntimeException("ORA-12170: TNS:Connect timeout occurred"))
                .thenReturn(1);
        when(payTxnDetailMapper.updateRefundSummary(anyString())).thenReturn(1);

        CompensateNotifyRespDTO response = service.compensateRefundQuery();

        assertEquals(2, response.getScanned(), "两条都 MUST 被扫到");
        assertEquals(1, response.getSubmitted(), "第二条 MUST 照样收口 —— 单条异常 NEVER 中断整批");
        assertEquals(1, response.getSkipped(), "抛异常那条 MUST 计入 failed，且只计一次");
        verify(payTxnDetailMapper, times(1)).updateRefundSummary("GT20260915000000002");
        verify(payTxnDetailMapper, never()).updateRefundSummary("GT20260915000000001");
    }

    /** 未配置退款查询地址：MUST 直接返错并且一条都不扫，NEVER 打到一个空地址上。 */

    @Test
    void missingRefundQueryUrlStopsBeforeScanning() {
        RefundDomainServiceImpl service = newService();
        properties.setRefundQueryUrl(null);

        CompensateNotifyRespDTO response = service.compensateRefundQuery();

        assertEquals("9001", response.getResultCode(),
                "缺配置 MUST 返系统错误码，让 Quartz 侧看得见；注意本 DTO 用的是 resultCode，不是 retCode");
        verify(payRefundDetailMapper, never()).selectCompensableRefundQuery(anyString(), anyInt(), anyInt());
    }
}
