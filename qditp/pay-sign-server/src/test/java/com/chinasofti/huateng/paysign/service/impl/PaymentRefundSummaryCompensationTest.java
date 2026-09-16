package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.paysign.mapper.PayRefundDetailMapper;
import com.chinasofti.huateng.paysign.mapper.PayTxnDetailMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 「退款汇总跨表对账」补偿的行为护栏。
 *
 * <p>它补的是 {@code requestRefund} 第 9 步（{@code updateRefundSummary}）失败或漏跑留下的窟窿：
 * {@code PAY_REFUND_DETAIL} 是唯一账本、已经对了，{@code PAY_TXN_DETAIL} 的 {@code REFUND_AMOUNT} /
 * {@code REFUND_STATUS} 两列汇总没跟上，于是账面上「可退金额 = 已付 - 已退」偏大。
 *
 * <p>只盯两件**改错了不会有编译错误、也不会有别的测试变红**的事：
 * <ul>
 *   <li><b>A 类（可自愈）MUST 按影响行数判成败</b> —— {@code updateRefundSummary} 返回 0 行
 *       意味着这一单在重算前已被别的路径改掉或已消失，把它计成 {@code submitted}
 *       等于对外宣称「这批账已经对齐了」，而实际一行都没动；</li>
 *   <li><b>B 类（不可自愈）MUST 一行都不改</b> —— 明细已 {@code SUCCESS} 而 {@code PAY_TXN_DETAIL}
 *       里根本没有该 {@code ORDER_NO} 时，{@code updateRefundSummary} 必然影响 0 行。
 *       在这里调它没有任何修复效果，却会把一批真正的坏账混进「已处理」的口径里。</li>
 * </ul>
 *
 * <p><b>刻意用 mock 而不是连库</b>：A 类在目标库里当前是 <b>0 行</b>（B 类 6 行），
 * 靠真实数据根本构造不出 A 类分支；且起 Spring 上下文会拉起 Druid 与 mybatis-adaptor，
 * 本机没有 Oracle 就跑不了，测试也就永远不会被真的执行。
 *
 * <p><b>本类刻意不注 PaySignProperties / PaySignGateway</b>：这条补偿**不出网**，
 * 只读写本地两张表。若哪天它需要网关，说明有人把它和 {@code compensateRefundQuery} 合并了 ——
 * 那两件事 NEVER 合并（理由见 {@code PaymentInternalController} 对应端点的 javadoc）。
 */
class PaymentRefundSummaryCompensationTest {

    private static final String DRIFTED_ORDER_NO = "GT20260915000000001";

    /** 取自目标库实测的 B 类 6 条之一（2026-08-26 那次生产事故当晚留下的）。 */
    private static final String ORPHAN_ORDER_NO = "GT20260826192157073586419";

    private final PayRefundDetailMapper payRefundDetailMapper = mock(PayRefundDetailMapper.class);
    private final PayTxnDetailMapper payTxnDetailMapper = mock(PayTxnDetailMapper.class);

    /**
     * 出向端口 <b>刻意传 {@code null}</b>（2026-09-16 起只剩这一个位置，此前是
     * {@code paySignProperties} + {@code paySignGateway} 两个，见 ADR-D113 续）。
     *
     * <p>这不是偷懒：本类要钉住的正是「汇总补偿只读写本地两张表、不碰支付中心」。改成构造注入后
     * （ADR-D96）这条约束比原先反射注入时**更硬** —— 谁把出向调用混进汇总路径，用例会立刻 NPE
     * 而不是静默走通。<b>NEVER 为了「看起来完整」把它换成真对象或 mock。</b></p>
     */
    private RefundDomainServiceImpl newService() {
        return new RefundDomainServiceImpl(
                payTxnDetailMapper,
                payRefundDetailMapper,
                null);
    }

    private void scanReturns(List<String> drifted, List<String> orphans) {
        when(payRefundDetailMapper.selectDriftedRefundSummary(anyString(), anyInt())).thenReturn(drifted);
        when(payRefundDetailMapper.selectOrphanRefundOrders(anyString(), anyInt())).thenReturn(orphans);
    }

    /** A 类命中且重算真的动了行：计入 submitted。 */
    @Test
    void driftedOrderWithAffectedRowCountsAsSubmitted() {
        RefundDomainServiceImpl service = newService();
        scanReturns(List.of(DRIFTED_ORDER_NO), List.of());
        when(payTxnDetailMapper.updateRefundSummary(DRIFTED_ORDER_NO)).thenReturn(1);

        CompensateNotifyRespDTO response = service.compensateRefundSummary();

        assertEquals(1, response.getScanned(), "扫到 1 条 A 类");
        assertEquals(1, response.getSubmitted(), "重算影响 1 行 MUST 计入已修");
        assertEquals(0, response.getSkipped(), "没有未收口的行");
        verify(payTxnDetailMapper, times(1)).updateRefundSummary(DRIFTED_ORDER_NO);
    }

    /**
     * A 类命中但重算影响 <b>0 行</b>：MUST 计 skipped，NEVER 计 submitted。
     *
     * <p>本类最关键的一条断言。0 行说明这一单在重算前已被别的路径改掉或已消失 ——
     * 判据 MUST 是「影响行数 &gt; 0」，NEVER 是「没抛异常」。
     */
    @Test
    void driftedOrderWithZeroAffectedRowIsSkippedNotSubmitted() {
        RefundDomainServiceImpl service = newService();
        scanReturns(List.of(DRIFTED_ORDER_NO), List.of());
        when(payTxnDetailMapper.updateRefundSummary(DRIFTED_ORDER_NO)).thenReturn(0);

        CompensateNotifyRespDTO response = service.compensateRefundSummary();

        assertEquals(1, response.getScanned(), "扫到 1 条 A 类");
        assertEquals(0, response.getSubmitted(),
                "影响 0 行 MUST NOT 计入已修 —— 一行都没动，却对外宣称账已对齐是最坏的结果");
        assertEquals(1, response.getSkipped(), "MUST 计入本轮未收口，留给下一轮重扫");
    }

    /** B 类命中：MUST 一行都不改，全部计 skipped。 */
    @Test
    void orphanOrderIsNeverTouchedAndAlwaysSkipped() {
        RefundDomainServiceImpl service = newService();
        scanReturns(List.of(), List.of(ORPHAN_ORDER_NO));

        CompensateNotifyRespDTO response = service.compensateRefundSummary();

        assertEquals(1, response.getScanned(), "扫到 1 条 B 类");
        assertEquals(0, response.getSubmitted(), "B 类 NEVER 计入已修：本任务不会自愈它");
        assertEquals(1, response.getSkipped(), "B 类 MUST 全部计入 skipped 等人工");
        verify(payTxnDetailMapper, never()).updateRefundSummary(anyString());
    }

    /** 两类都空：scanned=0，调用方据此停止重复调用。 */
    @Test
    void bothScansEmptyReportsNothingScanned() {
        RefundDomainServiceImpl service = newService();
        scanReturns(List.of(), List.of());

        CompensateNotifyRespDTO response = service.compensateRefundSummary();

        assertEquals(0, response.getScanned(), "两类都空时 scanned MUST 为 0，否则调用方永远停不下来");
        assertEquals(0, response.getSubmitted(), "没有可修的行");
        assertEquals(0, response.getSkipped(), "没有跳过的行");
        verify(payTxnDetailMapper, never()).updateRefundSummary(anyString());
    }
}
