package com.chinasofti.huateng.gatetxnpay.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.model.app.RequestUserAccInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestUserAccInfoResult;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 钉住只读查询侧三处「参数被静默改写」的逻辑：分页钳制、IF8A-35 统计窗口、列表 DTO 的空值补齐。
 *
 * <p>这三处的共同特征是**出错不报错**：分页钳制失效只是查得多或查得少、窗口算错只是统计口径变了、
 * DTO 补齐漏掉只是前端显示 null，编译与集成冒烟都发现不了，因此在把查询侧拆成独立类之前
 * MUST 先把它们钉住 —— 本测试就是那道网，<b>拆分时断言值 NEVER 改</b>。
 *
 * <p>这些方法已随查询侧拆分搬到 {@link GateTxnPayQueryServiceImpl}，实现类只依赖
 * {@link GateTxnPayMapper} 一个协作者，所以这里直接调公开方法、只 mock mapper，不需要反射。
 * <b>拆分前后本文件的断言值一个都没改</b> —— 那正是「只搬位置、没改行为」的证据。
 */
class GateTxnPayQueryTest {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final GateTxnPayMapper mapper = mock(GateTxnPayMapper.class);

    /** 四个搜索维度全空时 MUST 直接拒绝，NEVER 落成全表分页扫描。 */
    @Test
    void pageWithoutSearchScopeIsRejectedBeforeTouchingDb() {
        ResultVO<Map<String, Object>> result =
                service().page(null, null, null, "01", "04", "SUCCESS", null, null, 1, 10);
        assertEquals(ResultVO.ILLEGAL_PARAMS_CODE, result.getCode());
        verifyNoInteractions(mapper);
    }

    /** 只给开始日期不给结束日期同样算「没有范围」—— 两者 MUST 成对。 */
    @Test
    void pageWithOnlyStartDateIsRejected() {
        ResultVO<Map<String, Object>> result =
                service().page(null, null, null, null, null, null, "20260901", null, 1, 10);
        assertEquals(ResultVO.ILLEGAL_PARAMS_CODE, result.getCode());
        verifyNoInteractions(mapper);
    }

    /**
     * 分页参数钳制：{@code pageSize} 上限 100、非法值回落 10、{@code pageNum} 下限 1。
     * 少了 {@code Math.min} 那一层，前台传 100000 就是一次百万行结果集。
     */
    @Test
    void pageClampsPageSizeAndPageNum() {
        when(mapper.selectOperationPage(any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt())).thenReturn(List.of());
        when(mapper.countOperationPage(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        ArgumentCaptor<Integer> offset = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);

        service().page(null, "C1", null, null, null, null, null, null, 0, 100000);
        verify(mapper).selectOperationPage(any(), any(), any(), any(), any(), any(), any(), any(),
                offset.capture(), limit.capture());
        assertEquals(100, limit.getValue(), "pageSize MUST 被钳到 100");
        assertEquals(0, offset.getValue(), "pageNum<1 MUST 归一到第 1 页，offset=0");

        GateTxnPayMapper second = mock(GateTxnPayMapper.class);
        when(second.selectOperationPage(any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt())).thenReturn(List.of());
        ArgumentCaptor<Integer> offset2 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> limit2 = ArgumentCaptor.forClass(Integer.class);
        service(second).page(null, "C1", null, null, null, null, null, null, 3, null);
        verify(second).selectOperationPage(any(), any(), any(), any(), any(), any(), any(), any(),
                offset2.capture(), limit2.capture());
        assertEquals(10, limit2.getValue(), "pageSize 为空 MUST 回落默认 10");
        assertEquals(20, offset2.getValue(), "第 3 页 * 每页 10 MUST 得 offset=20");
    }

    /** IF8A-35 缺 thirdUserId 时 MUST 返 9002，NEVER 去查库。 */
    @Test
    void userAccInfoRejectsMissingThirdUserId() {
        RequestUserAccInfoResult result = service().requestUserAccInfo(new RequestUserAccInfoReqDTO());
        assertEquals("9002", result.getRetCode());
        verifyNoInteractions(mapper);
    }

    /**
     * 统计窗口下限 MUST 是 {@code yyyyMMdd} 字符串、且等于「今天减 N 个月」；
     * 配置非法（<=0）时回落 3 个月，<b>NEVER 退化成不加下限</b>（那会扫全部月分区）。
     */
    @Test
    void userAccInfoBuildsStartDateFromConfiguredMonths() {
        RequestUserAccInfoResult counted = new RequestUserAccInfoResult();
        counted.setUnpaidCount(2);
        counted.setFailureCount(1);
        when(mapper.countUserAccInfo(anyString(), anyString())).thenReturn(counted);

        RequestUserAccInfoReqDTO request = new RequestUserAccInfoReqDTO();
        request.setThirdUserId("U1");

        GateTxnPayQueryServiceImpl service = service();
        setMonths(service, 6);
        RequestUserAccInfoResult result = service.requestUserAccInfo(request);

        assertEquals("0000", result.getRetCode());
        assertEquals(2, result.getUnpaidCount());
        assertEquals(1, result.getFailureCount());
        verify(mapper).countUserAccInfo("U1", LocalDate.now().minusMonths(6).format(YYYYMMDD));

        GateTxnPayMapper fallbackMapper = mock(GateTxnPayMapper.class);
        when(fallbackMapper.countUserAccInfo(anyString(), anyString())).thenReturn(counted);
        GateTxnPayQueryServiceImpl fallback = service(fallbackMapper);
        setMonths(fallback, 0);
        fallback.requestUserAccInfo(request);
        verify(fallbackMapper).countUserAccInfo("U1", LocalDate.now().minusMonths(3).format(YYYYMMDD));
    }

    /**
     * 聚合查询返回 null 时 MUST 报错码 9002。
     *
     * <p>注意 {@code unpaidCount} / {@code failureCount} 是 <b>primitive int</b>，
     * 报错分支里它们照样是 0 —— 因此**唯一**能让 APP 区分「无欠费」与「查询失败」的就是 retCode。
     * 谁把这里改成返 0000，APP 立刻会把查询失败当成无欠费放行欠费乘客，而两个计数字段看不出差别。
     */
    @Test
    void userAccInfoNeverReportsSuccessWhenAggregateIsNull() {
        when(mapper.countUserAccInfo(anyString(), anyString())).thenReturn(null);
        RequestUserAccInfoReqDTO request = new RequestUserAccInfoReqDTO();
        request.setThirdUserId("U1");

        RequestUserAccInfoResult result = service().requestUserAccInfo(request);
        assertEquals("9002", result.getRetCode(), "查询未执行 MUST 用 retCode 表达，NEVER 返 0000");
        assertEquals("查询用户账务信息失败", result.getRetMsg());
    }

    /**
     * 列表 DTO 对历史行的空值补齐：{@code COUNTING_TIMES} 补 0、{@code COUNTING_FLAG} 补 N。
     * 2026-09-10 之前落库的行这两列是 NULL，去掉补齐前台会显示 null。
     */
    @Test
    void listDtoFillsLegacyCountingColumns() {
        GateTxnPay record = new GateTxnPay();
        record.setOrderNo("GT20260913100000000123456");
        record.setDebitStatus("SUCCESS");
        record.setTrxAmount(400);
        record.setOvertimeAmount(0);
        record.setTotalAmount(400);
        record.setCountingTimes(null);
        record.setCountingFlag("  ");
        when(mapper.selectByOrderNo("GT20260913100000000123456")).thenReturn(record);

        GateTxnPayListDTO dto = service().selectByOrderNo("  GT20260913100000000123456  ");
        assertEquals("GT20260913100000000123456", dto.getOrderNo(), "订单号 MUST 先 trim 再查");
        assertEquals(0, dto.getCountingTimes());
        assertEquals("N", dto.getCountingFlag());
        assertEquals(400, dto.getTotalAmount());
    }

    /** 订单号为空直接返 null，不查库。 */
    @Test
    void selectByOrderNoShortCircuitsOnBlank() {
        assertNull(service().selectByOrderNo("   "));
        verifyNoInteractions(mapper);
    }

    private GateTxnPayQueryServiceImpl service() {
        return service(mapper);
    }

    /**
     * 查询侧只用到 {@link GateTxnPayMapper}，构造器也只收这一个参数。
     * 一旦有人把写入、算价或 pay-sign 调用挪进查询实现，构造器就得多收协作者、本方法立刻编译不过 ——
     * 那正是要暴露的耦合。
     */
    private GateTxnPayQueryServiceImpl service(GateTxnPayMapper gateTxnPayMapper) {
        return new GateTxnPayQueryServiceImpl(gateTxnPayMapper);
    }

    /** {@code accInfoQueryMonths} 是 {@code @Value} 字段而非构造参数，单测只能反射注入。 */
    private void setMonths(GateTxnPayQueryServiceImpl service, int months) {
        try {
            Field field = GateTxnPayQueryServiceImpl.class.getDeclaredField("accInfoQueryMonths");
            field.setAccessible(true);
            field.setInt(service, months);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("accInfoQueryMonths 字段名已变更", e);
        }
    }
}
