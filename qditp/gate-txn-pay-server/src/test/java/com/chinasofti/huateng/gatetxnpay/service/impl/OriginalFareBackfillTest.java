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

/**
 * 钉住运营补数 {@code backfillOriginalFare} 的四类「出错不报错」行为：入参护栏、dryRun 默认值、
 * limit 钳制、可疑差额与幂等/失败分流。
 *
 * <p>这块逻辑的危险性在于**它是写接口且没有鉴权**（见 `GateTxnPayPageController` 的 javadoc），
 * 一旦 dryRun 默认值被改成 false、或差额阈值判断被简化，运营点一下就会把脏数据直接写进
 * 历史订单的 {@code ORIGINAL_FARE}，而 APP 账单里的「优惠」是拿它减出来的 —— 错了没有任何报警。
 *
 * <p>本文件先于「把补数抽成独立协作者」写成，抽完后<b>断言值 NEVER 改</b>：断言不变是行为没变的证据。
 */
class OriginalFareBackfillTest {

    private static final String START = "20260901";
    private static final String END = "20260910";

    private final GateTxnPayMapper mapper = mock(GateTxnPayMapper.class);
    private final FareCalculator fareCalculator = mock(FareCalculator.class);

    /** 入参护栏：请求体为空、日期非 yyyyMMdd、start 晚于 end，三者都 MUST 在碰库之前挡掉。 */
    @Test
    void illegalRequestNeverTouchesDb() {
        assertEquals(ResultVO.ILLEGAL_PARAMS_CODE, service().backfillOriginalFare(null).getCode());
        assertEquals(ResultVO.ILLEGAL_PARAMS_CODE, service().backfillOriginalFare(request("2026-09-01", END)).getCode());
        assertEquals(ResultVO.ILLEGAL_PARAMS_CODE, service().backfillOriginalFare(request("202609", END)).getCode());
        assertEquals(ResultVO.ILLEGAL_PARAMS_CODE, service().backfillOriginalFare(request(END, START)).getCode());
        verifyNoInteractions(mapper);
        verifyNoInteractions(fareCalculator);
    }

    /**
     * {@code dryRun} 为 null MUST 当 true。这是本接口唯一的安全默认值：
     * 反过来（null 当 false）意味着运营只填日期就直接落库，且**没有鉴权拦着**。
     */
    @Test
    void dryRunDefaultsToTrueAndWritesNothing() {
        stub(row("GT1", 400), 500);

        Map<String, Object> data = data(service().backfillOriginalFare(request(START, END)));

        assertEquals(true, data.get("dryRun"), "dryRun 为 null MUST 落到 true");
        assertEquals(1, data.get("updatedCount"), "试算也要把命中行放进 updatedList 供预览");
        verify(mapper, never()).updateOriginalFareIfNull(anyString(), anyString(), anyInt());
    }

    /** limit：null 落 500、超上限收到 5000、小于 1 抬到 1。每行都要调一次 para-server，钳制是保护对端。 */
    @Test
    void limitIsClampedIntoOneToFiveThousand() {
        assertEquals(500, capturedLimit(null));
        assertEquals(5000, capturedLimit(999999));
        assertEquals(1, capturedLimit(0));
        assertEquals(1, capturedLimit(-10));
    }

    /**
     * 可疑差额：实付 &gt; 0 且「原价 - 实付」超阈值的行 MUST 只进 suspectList、**不落库**。
     *
     * <p>这条挡的是已发生过的脏数据：设备 206377 的 5 笔把 {@code TRX_AMOUNT} 按元上送，
     * 原价按分，回填后 APP 的优惠虚高 4~7 元。</p>
     */
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

    /**
     * 实付为 0 的行 NEVER 进 suspectList：免扣费与日票的实付本来就是 0，
     * 「差额等于原价」是正常形态，不是脏数据。把这条判反会让整批日票永远补不上原价。
     */
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

    /**
     * UPDATE 影响 0 行是**幂等结果不是失败**：并发下已被别的调用填过。
     * 计进 failedCount 会让运营以为出错并反复重跑。
     */
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

    /**
     * 单笔抛异常 MUST 只落进 failedList 并带上 error，**不中断后续行**：
     * 本方法不带事务、逐笔自动提交，中途 return 会让剩下的行白扫一遍 para-server。
     * 同时 {@code queryOrderByBizKey} 查不到票价的行只累加 noFareCount，也不算失败。
     */
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

    /**
     * 补数只需要 {@code gateTxnPayMapper} 与 {@link FareCalculator} 两个协作者，构造器也只收这两个。
     * 谁把 pay-sign 调用或写入器牵进来，构造器就得加参数、本方法立刻编译不过 —— 那正是要暴露的耦合。
     */
    private OriginalFareBackfillServiceImpl service(GateTxnPayMapper gateTxnPayMapper) {
        return new OriginalFareBackfillServiceImpl(gateTxnPayMapper, fareCalculator);
    }
}
