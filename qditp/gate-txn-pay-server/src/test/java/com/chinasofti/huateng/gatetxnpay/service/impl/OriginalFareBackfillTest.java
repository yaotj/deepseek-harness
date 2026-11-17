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

class OriginalFareBackfillTest {

    private static final String START = "20260901";
    private static final String END = "20260910";

    private final GateTxnPayMapper mapper = mock(GateTxnPayMapper.class);
    private final FareCalculator fareCalculator = mock(FareCalculator.class);

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

    @Test
    void limitIsClampedIntoOneToFiveThousand() {
        assertEquals(500, capturedLimit(null));
        assertEquals(5000, capturedLimit(999999));
        assertEquals(1, capturedLimit(0));
        assertEquals(1, capturedLimit(-10));
    }

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

    private OriginalFareBackfillServiceImpl service(GateTxnPayMapper gateTxnPayMapper) {
        return new OriginalFareBackfillServiceImpl(gateTxnPayMapper, fareCalculator);
    }
}
