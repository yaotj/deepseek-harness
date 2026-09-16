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

/**
 * 「PROCESSING 退款回查」补偿的行为护栏（批次 5B，与 {@code requestRefund} 摘事务同批）。
 *
 * <p>只盯三件**改错了不会有编译错误、也不会有别的测试变红**的事：
 * <ul>
 *   <li>收口 CAS 命中 0 行时 <b>NEVER 当成功</b>，且 <b>NEVER 继续重算汇总</b> ——
 *       0 行意味着这一行已被退款回调或另一副本收口，继续算就是用本次回查结果覆盖别人写对的口径；</li>
 *   <li>真收口成功时 <b>MUST 无条件重算一次汇总</b> —— 摘掉事务后「明细已 SUCCESS、汇总没重算」
 *       这个中间态只有这里能兜住，省掉那一次等于 ADR-D8 只做了一半；</li>
 *   <li>回查没拿到终态时只推退避时间、<b>NEVER 落终态</b>。</li>
 * </ul>
 *
 * <p><b>刻意不起 Spring 上下文</b>（与 {@code PaySignFacadeFixture} 同一理由）：起上下文会拉起
 * Druid 与 mybatis-adaptor，本机没有 Oracle 就跑不了，测试也就永远不会被真的执行。
 *
 * <p>网关的 {@code isSuccess} 用 {@code thenCallRealMethod}：成功码判定必须与生产一致，
 * <b>NEVER 改成 {@code thenReturn(true)}</b>，否则成功码规则一改测试仍然全绿。
 */
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
        // 出向端口用真实现、内部包同一批 properties / paySignGateway（ADR-D113 续）：
        // URL 选取与成功码判定口径与收口前逐字相同，本类 4 条断言一字未改。
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
