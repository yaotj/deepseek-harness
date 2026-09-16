package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PayRefundDetail;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PayRefundDetailMapper {
    /**
     * 新增退款明细。
     */
    int insert(PayRefundDetail record);

    /**
     * 调用支付中心前标记退款处理中并增加请求次数。
     */
    int markRequesting(@Param("refundOrderNo") String refundOrderNo,
                       @Param("txnDate") String txnDate,
                       @Param("requestBody") String requestBody);

    /**
     * 回写本次退款请求结果。
     */
    int updateRequestResult(PayRefundDetail record);

    /**
     * 补偿扫表：捞出停在 {@code PROCESSING} 且到点可回查的退款明细。
     *
     * @param txnDateFrom  交易日期下界（{@code yyyyMMdd}），既做分区裁剪也做「扫多久以前的单」的上限
     * @param staleMinutes 距最近一次发起退款至少多少分钟才回查，避开正常同步应答的时间窗
     * @param limit        单轮上限
     */
    List<PayRefundDetail> selectCompensableRefundQuery(@Param("txnDateFrom") String txnDateFrom,
                                                       @Param("staleMinutes") int staleMinutes,
                                                       @Param("limit") int limit);

    /**
     * 回查确认为终态后收口（CAS：仅 {@code PROCESSING} 可推进）。
     *
     * <p>影响 0 行意味着这条已被别的路径（退款回调 / 另一个副本）收口，
     * <b>调用方 MUST NOT 把 0 行当成功继续</b>，尤其不能据此去重算汇总。</p>
     */
    int finishFromQuery(PayRefundDetail record);

    /**
     * 回查未得终态时只推下次回查时间（CAS：仅 {@code PROCESSING} 可推进）。
     *
     * <p><b>刻意不动 REQUEST_COUNT</b>：那一列的语义是「已发起退款请求次数」，
     * 回查不是发起退款，混进去会让运维分不清「我方重复退了几次」。</p>
     */
    int delayNextRefundQuery(@Param("refundOrderNo") String refundOrderNo,
                             @Param("txnDate") String txnDate,
                             @Param("delaySeconds") int delaySeconds);

    /**
     * 跨表对账扫表 <b>A 类（可自愈）</b>：捞出「明细已 {@code SUCCESS} 的退款总额」与
     * {@code PAY_TXN_DETAIL.REFUND_AMOUNT} 不相等、<b>且原支付订单确实存在</b>的 {@code ORDER_NO}。
     *
     * <p>这一类是 {@code requestRefund} 第 9 步（{@code updateRefundSummary}）失败或漏跑留下的：
     * 明细是唯一账本、已经对了，只有 {@code PAY_TXN_DETAIL} 那两列汇总没跟上，
     * 于是「可退金额 = 已付 - 已退」偏大。逐单重算一次即收口。
     *
     * <p><b>只返回订单号</b>：调用方拿到就直接 {@code updateRefundSummary(orderNo)}，
     * 金额与状态一律由那条 SQL 从明细表重算。<b>NEVER 为它新建投影类 / DTO</b> ——
     * 多带一个「我方算出的差额」字段就等于给了调用方一个「按差额补一下」的入口，
     * 而那条路没有幂等键（见 {@code PayTxnDetailMapper#updateRefundSummary} 的注释）。
     *
     * <p><b>已知盲区：{@code TXN_DATE} 畸形的行永远扫不到。</b>{@code TXN_DATE} 是本表的分区键，
     * 本语句 MUST 带 {@code TXN_DATE >= txnDateFrom} 才有分区裁剪；而库里存在少量把
     * {@code ORDER_NO} 前 8 位当成日期写进去的历史行（形如 {@code 28063829} / {@code 28064044}，
     * 不是 {@code yyyyMMdd}）。字符串比较下这类值<b>恒小于</b>任何 {@code 2026xxxx}，
     * 因此它们被下界永久排除、本任务永远不会碰到，<b>只能人工处理</b>。
     * 其中 {@code 280640445149511680} 同时也是下面 B 类的成员之一。
     * <b>NEVER 为了捞它们去掉 TXN_DATE 下界</b> —— 那等于让这条 SQL 全分区扫。
     *
     * @param txnDateFrom 交易日期下界（{@code yyyyMMdd}），既做分区裁剪也是「只对最近多久的账」
     * @param limit       单轮上限
     */
    List<String> selectDriftedRefundSummary(@Param("txnDateFrom") String txnDateFrom,
                                            @Param("limit") int limit);

    /**
     * 跨表对账扫表 <b>B 类（不可自愈）</b>：明细表里有 {@code REFUND_STATUS='SUCCESS'} 的行，
     * 但 {@code PAY_TXN_DETAIL} 里<b>根本没有</b>这个 {@code ORDER_NO}。
     *
     * <p><b>这一类 MUST NOT 尝试修</b>：{@code updateRefundSummary} 对它们影响 0 行
     * （没有行可 UPDATE），把 0 行当成「已修好」会让一批真正的坏账从告警里消失。
     * 调用方 MUST 只记 WARN + 计入 skipped，等人工核对「这笔退款退的到底是哪张原单」。
     *
     * <p>盲区与 A 类同源，见 {@link #selectDriftedRefundSummary} 的说明。
     *
     * @param txnDateFrom 交易日期下界（{@code yyyyMMdd}），同上
     * @param limit       单轮上限
     */
    List<String> selectOrphanRefundOrders(@Param("txnDateFrom") String txnDateFrom,
                                          @Param("limit") int limit);
}
