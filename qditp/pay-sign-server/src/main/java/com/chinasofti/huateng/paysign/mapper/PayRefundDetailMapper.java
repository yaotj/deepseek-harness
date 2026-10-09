package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PayRefundDetail;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PayRefundDetailMapper {
    /** 新增退款明细。 */
    int insert(PayRefundDetail record);

    /** 调用支付中心前标记退款处理中并增加请求次数。 */
    int markRequesting(@Param("refundOrderNo") String refundOrderNo,
                       @Param("txnDate") String txnDate,
                       @Param("requestBody") String requestBody);

    /** 回写本次退款请求结果。 */
    int updateRequestResult(PayRefundDetail record);

    /**
     * 同一原支付订单下**在途**（未达终态）的退款明细行数，用于发起退款前的重复提交拦截。
     *
     * <p>在途集合是 {@code INIT} / {@code PROCESSING} / {@code RETRY} 三个，
     * **NEVER 收窄成只看 {@code PROCESSING}**：
     * <ul>
     *   <li>{@code INIT} 是 {@code insert} 后、{@code markRequesting} 前的窗口，进程在这中间挂掉就永久停在这个值；</li>
     *   <li>{@code RETRY} 是 2026-09-22 之前旧版本的落法（现已不再写入，但库里有 17 行历史数据）。</li>
     * </ul>
     * 这三个状态**都不会**被 {@code selectDriftedRefundSummary} 计入 {@code PAY_TXN_DETAIL.REFUND_AMOUNT}，
     * 因此「按已退金额判可退额」那条校验（{@code PayRefundRules.validateRefundPayTxn}）对它们完全透明 ——
     * 这正是同一原单被重复提交 3~4 次退款的成因（2026-09-22 实测 7 个原单有多行退款）。
     */
    int countInFlightByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 补偿扫表：捞出停在非终态且到点可回查的退款明细。
     *
     * <p>状态白名单是 {@code PROCESSING} + {@code RETRY}：后者是旧版本遗留、**当前代码已不再写入**，
     * 但库里那 17 行在纳入本扫描之前**没有任何路径会碰它们**（本查询原先只取 {@code PROCESSING}、
     * {@code selectDriftedRefundSummary} 只取 {@code SUCCESS}）⇒ 永久悬挂。
     * **NEVER 把 {@code RETRY} 从白名单里删掉**，否则那批行重新变成死区。
     *
     * <p>{@code INIT} **刻意不在白名单里**：它表示请求从未发出，回查必然「未找到数据」、
     * 只会白耗退避轮次。那类行的处置是人工核对后重发或关单，属已知缺口。
     *
     * @param txnDateFrom  交易日期下界（{@code yyyyMMdd}），既做分区裁剪也做「扫多久以前的单」的上限
     * @param staleMinutes 距最近一次发起退款至少多少分钟才回查，避开正常同步应答的时间窗
     * @param limit        单轮上限
     */
    List<PayRefundDetail> selectCompensableRefundQuery(@Param("txnDateFrom") String txnDateFrom,
                                                       @Param("staleMinutes") int staleMinutes,
                                                       @Param("limit") int limit);

    /** 回查确认为终态后收口（CAS：仅 {@code PROCESSING} / {@code RETRY} 可推进）。 */
    int finishFromQuery(PayRefundDetail record);

    /** 回查未得终态时只推下次回查时间（CAS：仅 {@code PROCESSING} / {@code RETRY} 可推进）。 */
    int delayNextRefundQuery(@Param("refundOrderNo") String refundOrderNo,
                             @Param("txnDate") String txnDate,
                             @Param("delaySeconds") int delaySeconds);

    /**
     * 回查超过龄期上限后的终态出口：置 {@code CLOSED} 并清空 {@code NEXT_REQUEST_TIME}
     * （CAS：仅 {@code PROCESSING} / {@code RETRY} 可推进）。
     *
     * <p>**为什么是 {@code CLOSED} 而不是 {@code FAIL}**：超龄的语义是「我方放弃自动定性、结果未知」，
     * 而 {@code FAIL} 表示「已确认没退成功」。落 {@code FAIL} 会让对账与运营把一笔可能已经退成功的单当成未退，
     * 进而再发一笔。{@code CLOSED} 同样不会被 {@code selectDriftedRefundSummary}（只取 {@code SUCCESS}）
     * 计入已退金额，因此不会污染 {@code PAY_TXN_DETAIL} 的汇总。
     * 该字面量库内已有先例（11 行，均为 2026-09-18 人工收口）。
     *
     * <p>**落这个状态即表示需要人工介入**，调用点 MUST 同时打 ERROR。
     */
    int exhaustFromQuery(@Param("refundOrderNo") String refundOrderNo,
                         @Param("txnDate") String txnDate,
                         @Param("payCenterCode") String payCenterCode,
                         @Param("payCenterMsg") String payCenterMsg);

    /**
     * 按**我方退款流水号**回查单行（2026-09-22 新增，服务 §5.2 退款回调）。
     *
     * <p>**为什么必须有它**：回调报文（{@code ReceiveRefundResultReqDTO}）里只有 {@code outRefundNo}
     * （= {@code REFUND_ORDER_NO}）、**没有 {@code txnDate}**，而本表的键是
     * {@code REFUND_ORDER_NO + TXN_DATE}、全部 CAS 语句都要两个值 —— 因此收口前 MUST 先用本方法把
     * {@code TXN_DATE} 与 {@code ORDER_NO} 取回来。
     *
     * <p>{@code REFUND_ORDER_NO} 由我方按雪花号生成、全局唯一，正常只会命中一行；
     * 仍写 {@code ROWNUM} 限 1 是**防脏数据**（历史人工插入过行），
     * 并按 {@code CREATE_TIME} 倒序取最新一条。
     */
    PayRefundDetail selectByRefundOrderNo(@Param("refundOrderNo") String refundOrderNo);

    /**
     * 跨表对账扫表 A 类（可自愈）：捞出「明细已 {@code SUCCESS} 的退款总额」与。
     *
     * @param txnDateFrom 交易日期下界（{@code yyyyMMdd}），既做分区裁剪也是「只对最近多久的账」
     * @param limit       单轮上限
     */
    List<String> selectDriftedRefundSummary(@Param("txnDateFrom") String txnDateFrom,
                                            @Param("limit") int limit);

    /**
     * 跨表对账扫表 B 类（不可自愈）：明细表里有 {@code REFUND_STATUS='SUCCESS'} 的行。
     *
     * @param txnDateFrom 交易日期下界（{@code yyyyMMdd}），同上
     * @param limit       单轮上限
     */
    List<String> selectOrphanRefundOrders(@Param("txnDateFrom") String txnDateFrom,
                                          @Param("limit") int limit);
}
