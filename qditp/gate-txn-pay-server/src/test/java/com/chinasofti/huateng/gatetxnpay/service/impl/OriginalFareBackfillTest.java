package com.chinasofti.huateng.gatetxnpay.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.fare.FareCalculator;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.gatetxnpay.model.page.OriginalFareBackfillRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 钉住运营补数 {@code backfillOriginalFare} 的四类「出错不报错」行为：入参护栏、dryRun 默认值、 limit 钳制、可疑差额与幂等/失败分流。 */
class OriginalFareBackfillTest {

    private static final String START = "20260901";
    private static final String END = "20260910";

    private final GateTxnPayMapper mapper = mock(GateTxnPayMapper.class);
    private final FareCalculator fareCalculator = mock(FareCalculator.class);

    /** 入参护栏：请求体为空、日期非 yyyyMMdd、start 晚于 end。 */
    @Test
    void illegalRequestNeverTouchesDb() {
        assertEquals(ResultVO.ILLEGAL_PARAMS_CODE, service().backfillOriginalFare(null).getCode());
        assertEquals(ResultVO.ILLEGAL_PARAMS_CODE, service().backfillOriginalFare(request("2026-09-01", END)).getCode());
        assertEquals(ResultVO.ILLEGAL_PARAMS_CODE, service().backfillOriginalFare(request("202609", END)).getCode());
        assertEquals(ResultVO.ILLEGAL_PARAMS_CODE, service().backfillOriginalFare(request(END, START)).getCode());
        verifyNoInteractions(mapper);
        verifyNoInteractions(fareCalculator);
    }

    @Test
    void dryRunDefaultsToTrueAndWritesNothing() {
        stub(row("GT1", 400), 500);

        Map<String, Object> data = data(service().backfillOriginalFare(request(START, END)));

        assertEquals(true, data.get("dryRun"), "dryRun 为 null MUST 落到 true");
        assertEquals(1, data.get("updatedCount"), "试算也要把命中行放进 updatedList 供预览");
        verify(mapper, never()).updateOriginalFareIfNull(anyString(), anyString(), anyInt());
    }

    /** limit：null 落 500、超上限收到 5000、小于 1 抬到 1。 */
    @Test
    void limitIsClampedIntoOneToFiveThousand() {
        assertEquals(500, capturedLimit(null));
        assertEquals(5000, capturedLimit(999999));
        assertEquals(1, capturedLimit(0));
        assertEquals(1, capturedLimit(-10));
    }

    /** 可疑差额：实付 &gt。 */
    @Test
    void suspiciousDiffIsParkedInsteadOfWritten() {
        stub(row("GT2", 4), 400);

        OriginalFareBackfillRequest request = request(START, END);
        request.setDryRun(false);
        Map<String, Object> data = data(service().backfillOriginalFare(request));

        assertEquals(1, data.get("suspectCount"));
        assertEquals(0, data.get("updatedCount"));
        verify(mapper, never()).updateOriginalFareIfNull(anyString(), anyString(), anyInt());
    }

    /** 「差额等于原价」是正常形态，不是脏数据。 */
    @Test
    void zeroPaidRowIsNotSuspicious() {
        stub(row("GT3", 0), 400);

        OriginalFareBackfillRequest request = request(START, END);
        request.setDryRun(false);
        when(mapper.updateOriginalFareIfNull(eq("GT3"), anyString(), eq(400))).thenReturn(1);
        Map<String, Object> data = data(service().backfillOriginalFare(request));

        assertEquals(0, data.get("suspectCount"), "实付 0 的行 MUST 照常回填");
        assertEquals(1, data.get("updatedCount"));
    }

    /** {@code force=true} 明确越过阈值：运营确认过差额属实时用，落库仍走同一条带 IS NULL 的 UPDATE。 */
    @Test
    void forceBypassesSuspectThreshold() {
        stub(row("GT4", 4), 400);

        OriginalFareBackfillRequest request = request(START, END);
        request.setDryRun(false);
        request.setForce(true);
        when(mapper.updateOriginalFareIfNull(eq("GT4"), anyString(), eq(400))).thenReturn(1);
        Map<String, Object> data = data(service().backfillOriginalFare(request));

        assertEquals(0, data.get("suspectCount"));
        assertEquals(1, data.get("updatedCount"));
    }

    /** UPDATE 影响 0 行是幂等结果不是失败：并发下已被别的调用填过。 */
    @Test
    void zeroAffectedRowCountsAsNeitherUpdatedNorFailed() {
        stub(row("GT5", 400), 500);

        OriginalFareBackfillRequest request = request(START, END);
        request.setDryRun(false);
        when(mapper.updateOriginalFareIfNull(anyString(), anyString(), anyInt())).thenReturn(0);
        Map<String, Object> data = data(service().backfillOriginalFare(request));

        assertEquals(0, data.get("updatedCount"));
        assertEquals(0, data.get("failedCount"));
    }

    /** 不中断后续行： 本方法不带事务、逐笔自动提交，中途 return 会让剩下的行白扫一遍 para-server。 */
    @Test
    void oneRowFailureNeitherAbortsNorSwallowsTheRest() {
        GateTxnPay boom = row("GT6", 400);
        GateTxnPay noFare = row("GT7", 400);
        GateTxnPay ok = row("GT8", 400);
        when(mapper.selectMissingOriginalFare(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(boom, noFare, ok));
        when(fareCalculator.queryOriginalFare(any(), any())).thenReturn(500, null, 500);
        when(mapper.updateOriginalFareIfNull(eq("GT6"), anyString(), anyInt()))
                .thenThrow(new IllegalStateException("ORA-00001"));
        when(mapper.updateOriginalFareIfNull(eq("GT8"), anyString(), anyInt())).thenReturn(1);

        OriginalFareBackfillRequest request = request(START, END);
        request.setDryRun(false);
        Map<String, Object> data = data(service().backfillOriginalFare(request));

        assertEquals(3, data.get("scannedCount"));
        assertEquals(1, data.get("failedCount"));
        assertEquals(1, data.get("noFareCount"), "查不到票价只累加 noFareCount，NEVER 计失败");
        assertEquals(1, data.get("updatedCount"), "失败行之后的行 MUST 继续处理");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failed = (List<Map<String, Object>>) data.get("failedList");
        assertTrue(String.valueOf(failed.get(0).get("error")).contains("ORA-00001"),
                "失败原因 MUST 回显给运营，否则只能翻日志");
    }

    private int capturedLimit(Integer requested) {
        GateTxnPayMapper fresh = mock(GateTxnPayMapper.class);
        when(fresh.selectMissingOriginalFare(anyString(), anyString(), anyInt())).thenReturn(List.of());
        OriginalFareBackfillRequest request = request(START, END);
        request.setLimit(requested);
        service(fresh).backfillOriginalFare(request);
        org.mockito.ArgumentCaptor<Integer> limit = org.mockito.ArgumentCaptor.forClass(Integer.class);
        verify(fresh).selectMissingOriginalFare(anyString(), anyString(), limit.capture());
        return limit.getValue();
    }

    private void stub(GateTxnPay row, Integer originalFare) {
        when(mapper.selectMissingOriginalFare(anyString(), anyString(), anyInt())).thenReturn(List.of(row));
        when(fareCalculator.queryOriginalFare(any(), any())).thenReturn(originalFare);
    }

    private GateTxnPay row(String orderNo, Integer trxAmount) {
        GateTxnPay row = new GateTxnPay();
        row.setOrderNo(orderNo);
        row.setTxnDate(START);
        row.setInStation("0101");
        row.setOutStation("0110");
        row.setTrxAmount(trxAmount);
        return row;
    }

    private OriginalFareBackfillRequest request(String startDate, String endDate) {
        OriginalFareBackfillRequest request = new OriginalFareBackfillRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        return request;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResultVO<Map<String, Object>> result) {
        assertEquals(ResultVO.SUCCESS_CODE, result.getCode());
        return (Map<String, Object>) result.getData();
    }

    private OriginalFareBackfillServiceImpl service() {
        return service(mapper);
    }

    /** 补数只需要 {@code gateTxnPayMapper} 与 {@link FareCalculator} 两个协作者，构造器也只收这两个。 */
    private OriginalFareBackfillServiceImpl service(GateTxnPayMapper gateTxnPayMapper) {
        return new OriginalFareBackfillServiceImpl(gateTxnPayMapper, fareCalculator);
    }
}
