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

    /** 支付结果查询的回写，WHERE 带「非终态」白名单（{@code PAY_STATUS} 为空或不等于 SUCCESS）。 */
    int updatePayQueryResultIfNotSuccess(@Param("orderNo") String orderNo,
                                        @Param("payStatus") String payStatus,
                                        @Param("tradeNo") String tradeNo,
                                        @Param("transTime") String transTime,
                                        @Param("payAmount") String payAmount,
                                        @Param("resultCode") String resultCode,
                                        @Param("resultMsg") String resultMsg);
    /** 按 ALIPAY_REFUND_LOG 重算原支付订单的已退款金额与退款状态。 */
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

    /** 按卡号统计未结清（PAY_STATUS 非 SUCCESS）的支付宝出行订单数。 */
    int countUnsettledByCardId(@Param("cardId") String cardId);
}
