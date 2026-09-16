package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.model.alipaytrip.AlipayPayLogDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AlipayPayLogMapper {
    AlipayPayLog selectByOrderNo(@Param("orderNo") String orderNo);
    AlipayPayLog selectByPaySeq(@Param("paySeq") String paySeq);
    int insert(AlipayPayLog payLog);
    int updatePayStatus(@Param("paySeq") String paySeq, @Param("payStatus") String payStatus);
    int updatePayNotify(@Param("orderNo") String orderNo, @Param("payStatus") String payStatus, @Param("tradeNo") String tradeNo, @Param("transTime") String transTime, @Param("payAmount") String payAmount, @Param("resultCode") String resultCode, @Param("resultMsg") String resultMsg);

    /**
     * 支付结果查询的回写，WHERE 带「非终态」白名单（{@code PAY_STATUS} 为空或不等于 SUCCESS）。
     *
     * <p>{@code payQuery} 是查询接口，<b>NEVER 用 {@link #updatePayNotify} 无条件覆盖</b>——
     * 那会把已经 SUCCESS 的订单按一次查不通的结果改成 FAIL，而 {@code PAY_STATUS} 正是
     * {@link #countUnsettledByCardId} 判断欠费的依据。</p>
     *
     * <p>返回 0 行有两种含义，调用方 <b>MUST 回读区分</b>：本地已是目标状态（幂等命中），
     * 或本地已 SUCCESS 而支付中心给出失败（口径冲突，只告警、等人工，NEVER 强行覆盖）。</p>
     */
    int updatePayQueryResultIfNotSuccess(@Param("orderNo") String orderNo,
                                        @Param("payStatus") String payStatus,
                                        @Param("tradeNo") String tradeNo,
                                        @Param("transTime") String transTime,
                                        @Param("payAmount") String payAmount,
                                        @Param("resultCode") String resultCode,
                                        @Param("resultMsg") String resultMsg);
    /**
     * 按 ALIPAY_REFUND_LOG 重算原支付订单的已退款金额与退款状态。
     *
     * <p>只传 orderNo：金额与状态都由 SQL 从明细表汇总，NEVER 由调用方传入增量——
     * 入参没有幂等键，传增量意味着重复执行会重复累加（详见 mapper XML 内注释）。</p>
     */
    int updateRefundSummary(@Param("orderNo") String orderNo);

    /**
     * 查询支付宝出行订单列表。
     */
    List<AlipayPayLog> selectAlipayPayLogList(@Param("thirdUserId") String thirdUserId,
                                              @Param("startTime") String startTime,
                                              @Param("endTime") String endTime,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit,
                                              @Param("debitRequestResult") String debitRequestResult,
                                              @Param("invoice") String invoice);

    /**
     * 查询支付宝出行订单总数。
     */
    int countAlipayPayLogList(@Param("thirdUserId") String thirdUserId,
                              @Param("startTime") String startTime,
                              @Param("endTime") String endTime,
                              @Param("debitRequestResult") String debitRequestResult,
                              @Param("invoice") String invoice);

    /**
     * 按进站交易ID查询支付流水。
     */
    AlipayPayLogDTO selectByEntryId(@Param("entryId") String entryId);

    /**
     * 按出站交易ID查询支付流水。
     */
    AlipayPayLogDTO selectByExitId(@Param("exitId") String exitId);

    /**
     * 按卡号统计未结清（PAY_STATUS 非 SUCCESS）的支付宝出行订单数。
     *
     * <p>供 blacklist-server 盘点「该卡欠费是否已结清」调用。支付宝出行链路的欠费**只落本表**，
     * `GATE_TXN_PAY` 里没有对应行（分流点见 {@code fep-dev-server} 的 GateTransactionHandler：
     * issueChannelCode=07 走支付宝、其余走闸机扣费），因此判定 MUST 同时查两张表。</p>
     *
     * <p>⚠️ 已知口径污染：{@code PaymentRequestService} 把支付中心的幂等拒答（如
     * 「订单已支付成功，请勿重复支付」）也当成扣款失败，连带把 PAY_STATUS 写成 FAIL。
     * 因此本表的 FAIL **不完全等于真欠费**，在该缺陷修复前，盘点结果只能人工复核，
     * **NEVER** 直接拿它驱动删除黑名单。</p>
     */
    int countUnsettledByCardId(@Param("cardId") String cardId);
}
