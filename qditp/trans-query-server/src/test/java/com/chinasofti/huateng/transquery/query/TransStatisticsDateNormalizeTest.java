package com.chinasofti.huateng.transquery.query;

import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.model.app.TripDataDTO;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IF8A-41 的日期归一回归用例（2026-09-22 / 1.0.14，1.0.15 放宽为双格式）。
 *
 * <p>钉住的是**下发给 gate-txn-pay 的值**，不是应答字段：下游拿它与 8 位的 {@code TXN_DATE} 做字符串比较，
 * 少了这一步归一时 {@code TXN_DATE <= '2026-09-22'} 恒 false、接口恒返 {@code count=0}。
 * 线上实测过的形态：同一用户同一窗口，{@code yyyy-MM-dd} 返 0 / 8 位返 3 笔。
 *
 * <p><b>两种入参都 MUST 放过</b>（1.0.15 业主裁决）：APP 对本接口实际送的是 8 位、对 IF8A-05 送的是带横线那种，
 * 入口原先只认后者 ⇒ 本接口对任何账号都返 {@code 8001}。<b>NEVER 把 8 位那条用例改回「期望 8001」。</b>
 */
class TransStatisticsDateNormalizeTest {

    private GateTxnPayClient gateTxnPayClient;
    private TransStatisticsQueryHandler handler;

    @BeforeEach
    void setUp() {
        gateTxnPayClient = mock(GateTxnPayClient.class);
        handler = new TransStatisticsQueryHandler();
        ReflectionTestUtils.setField(handler, "gateTxnPayClient", gateTxnPayClient);
        ReflectionTestUtils.setField(handler, "paramNormalizer", new TransQueryParamNormalizer());
    }

    @Test
    void contractDateIsNormalizedToEightDigitsBeforeRpc() {
        when(gateTxnPayClient.requestTransStatistics(any())).thenReturn(statistics());

        RequestTransStatisticsResult result = handler.requestTransStatistics(request("2026-09-01", "2026-09-22"));

        assertEquals("0000", result.getRetCode());
        ArgumentCaptor<RequestTransStatisticsReqDTO> captor =
                ArgumentCaptor.forClass(RequestTransStatisticsReqDTO.class);
        verify(gateTxnPayClient).requestTransStatistics(captor.capture());
        assertEquals("20260901", captor.getValue().getStartDate());
        assertEquals("20260922", captor.getValue().getEndDate());
    }

    /** 8 位日期原样下发、NEVER 再被拒成 8001（APP 实际送的就是这种）。 */
    @Test
    void compactDateIsAcceptedAndPassedThrough() {
        when(gateTxnPayClient.requestTransStatistics(any())).thenReturn(statistics());

        RequestTransStatisticsResult result = handler.requestTransStatistics(request("20260901", "20260922"));

        assertEquals("0000", result.getRetCode());
        ArgumentCaptor<RequestTransStatisticsReqDTO> captor =
                ArgumentCaptor.forClass(RequestTransStatisticsReqDTO.class);
        verify(gateTxnPayClient).requestTransStatistics(captor.capture());
        assertEquals("20260901", captor.getValue().getStartDate());
        assertEquals("20260922", captor.getValue().getEndDate());
    }

    /** 既不是 8 位也不是 yyyy-MM-dd 的一律拒成 8001，且 NEVER 下发 RPC。 */
    @Test
    void malformedDateIsRejectedAtEntry() {
        RequestTransStatisticsResult result = handler.requestTransStatistics(request("2026/09/01", "2026/09/22"));

        assertEquals("8001", result.getRetCode());
        verify(gateTxnPayClient, never()).requestTransStatistics(any());
    }

    /** 起止倒置同样拒成 8001，不下发 RPC。 */
    @Test
    void reversedRangeIsRejected() {
        RequestTransStatisticsResult result = handler.requestTransStatistics(request("2026-09-22", "2026-09-01"));

        assertEquals("8001", result.getRetCode());
        verify(gateTxnPayClient, never()).requestTransStatistics(any());
    }

    /** 不传日期时两端都置 null，下游不拼日期谓词 —— NEVER 补一个默认窗口。 */
    @Test
    void absentDatesStayNull() {
        when(gateTxnPayClient.requestTransStatistics(any())).thenReturn(statistics());

        handler.requestTransStatistics(request(null, null));

        ArgumentCaptor<RequestTransStatisticsReqDTO> captor =
                ArgumentCaptor.forClass(RequestTransStatisticsReqDTO.class);
        verify(gateTxnPayClient).requestTransStatistics(captor.capture());
        assertEquals(null, captor.getValue().getStartDate());
        assertEquals(null, captor.getValue().getEndDate());
    }

    private RequestTransStatisticsReqDTO request(String startDate, String endDate) {
        RequestTransStatisticsReqDTO request = new RequestTransStatisticsReqDTO();
        request.setThirdUserId("00522950");
        request.setCardType("12");
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        return request;
    }

    private RequestTransStatisticsResult statistics() {
        RequestTransStatisticsResult result = new RequestTransStatisticsResult();
        TripDataDTO tripData = new TripDataDTO();
        tripData.setCount(3);
        tripData.setTotalPrice("11");
        result.setTripData(tripData);
        return result;
    }
}
