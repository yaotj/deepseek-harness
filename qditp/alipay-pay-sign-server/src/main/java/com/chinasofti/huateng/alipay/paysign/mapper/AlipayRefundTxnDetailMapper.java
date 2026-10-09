package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundTxnDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code ALIPAY_REFUND_TXN_DETAIL} 数据访问 —— <b>只覆盖库内实际的 18 列</b>（2026-09-20 按
 * {@code AFCITPDB} 实测收窄，理由与删除清单见 {@link AlipayRefundTxnDetail} 的类注释）。
 *
 * <p>形态对齐 pay-sign-server 的 {@code PayRefundDetailMapper}，但**列不对齐** —— 那张表有
 * {@code RET_CODE} / {@code PAY_CENTER_CODE} / {@code REQUEST_BODY} 等列，本表没有，
 * <b>NEVER 照它补语句</b>。
 */
@Mapper
public interface AlipayRefundTxnDetailMapper {

    int insert(AlipayRefundTxnDetail record);

    AlipayRefundTxnDetail selectByRefundOrderNo(@Param("refundOrderNo") String refundOrderNo);

    List<AlipayRefundTxnDetail> selectByOrderNo(@Param("orderNo") String orderNo);

    /** 已退成功金额合计（分）。 */
    Integer sumSuccessRefundAmount(@Param("orderNo") String orderNo);

    /** 按原订单号 + 状态计数，退款申请的幂等短路。 */
    int countByOrderNoAndStatus(@Param("orderNo") String orderNo,
                                @Param("refundStatus") String refundStatus);

    /**
     * 出网前留痕：请求次数 +1、状态置 {@code PROCESSING}。
     *
     * <p><b>本方法不落请求报文</b>（本表没有 {@code REQUEST_BODY} 列）。报文 MUST 由调用方写
     * {@code ALIPAY_PAY_CENTER_MSG_LOG}、一次调用一行，**NEVER 再给本方法加 requestBody 参数**。
     */
    int markRequesting(@Param("refundOrderNo") String refundOrderNo,
                       @Param("txnDate") String txnDate);

    /** 回写支付中心退款受理结果；应答码不在本表，判对端怎么答的去 {@code ALIPAY_PAY_CENTER_MSG_LOG}。 */
    int updateRequestResult(AlipayRefundTxnDetail record);

    /**
     * 退款回调收口：把 {@code PROCESSING} 明细 CAS 成终态。
     *
     * <p><b>WHERE 里带 {@code REFUND_STATUS = 'PROCESSING'}，这就是幂等地基</b>：支付中心重推第二次
     * 影响 0 行，调用方据此判「已收口过」。<b>NEVER 去掉那个状态谓词</b> —— 去掉后重推会把已经
     * {@code SUCCESS} 的单子按后到的报文覆盖成 {@code FAIL}。
     *
     * <p><b>刻意不带 {@code TXN_DATE}</b>（与 {@link #markRequesting} / {@link #updateRequestResult} 不同）：
     * 回调报文里只有 {@code outRefundNo}、没有交易日，而唯一索引
     * {@code UK_ARTD_REFUND_ORDER(REFUND_ORDER_NO, TXN_DATE)} 的**前导列就是退款单号**，仍走索引。
     * <b>NEVER 为了「凑齐两列」去拿当天日期当 TXN_DATE</b> —— 跨日回调会一行都命中不到。
     */
    int settleFromCallback(@Param("refundOrderNo") String refundOrderNo,
                           @Param("refundStatus") String refundStatus,
                           @Param("remark") String remark);

    /**
     * 退款回查补偿的扫表：取一批停在 {@code PROCESSING} 且已过静默期、已过退避时间的明细。
     *
     * <p><b>与旧表（{@code ALIPAY_REFUND_LOG}）那条同名语句刻意不同</b>：本表有
     * {@code NEXT_REQUEST_TIME} / {@code LAST_REQUEST_TIME} / {@code REQUEST_COUNT} 三个退避列，
     * 因此扫表谓词里带 {@code NEXT_REQUEST_TIME} 闸门、回查未得终态时由
     * {@link #delayNextRefundQuery} 推时间；旧表没有那些列、只能靠 {@code UPDATE_TIME} 静默期兜。
     * <b>NEVER 照旧表把退避条件删掉</b> —— 删掉后一条永远拿不到终态的单子会每轮都打一次支付中心。
     *
     * @param scanDays     只回查最近 N 天创建的退款
     * @param staleMinutes 距上次更新至少多少分钟才回查（给正常回调留时间）
     * @param limit        单轮上限，限流在 SQL 的 {@code ROWNUM} 里、不在 Java 里截断
     */
    List<AlipayRefundTxnDetail> selectCompensableRefundQuery(@Param("scanDays") int scanDays,
                                                            @Param("staleMinutes") int staleMinutes,
                                                            @Param("limit") int limit);

    /**
     * 回查未得终态时把下次回查时间往后推。
     *
     * <p>WHERE 里同样带 {@code REFUND_STATUS = 'PROCESSING'}：期间被回调收口的行不该被这条语句碰。
     * <b>刻意不动 {@code UPDATE_TIME}</b> —— 那一列是「状态最后一次真实变化」的时间，
     * 回查没改状态就不该刷它，否则静默期闸门与退避闸门会互相叠加、行为不可预测。
     */
    int delayNextRefundQuery(@Param("refundOrderNo") String refundOrderNo,
                             @Param("delaySeconds") int delaySeconds);
}
