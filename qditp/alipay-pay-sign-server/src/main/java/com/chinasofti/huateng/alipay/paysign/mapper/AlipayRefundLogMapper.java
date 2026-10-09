package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlipayRefundLogMapper {
    AlipayRefundLog selectByRefundOrderNo(@Param("refundOrderNo") String refundOrderNo);
    AlipayRefundLog selectByOrderNo(@Param("orderNo") String orderNo);
    int insert(AlipayRefundLog refundLog);

    /** 统计同一原订单下处于指定退款状态的明细条数。 */
    int countByOrderNoAndStatus(@Param("orderNo") String orderNo, @Param("refundStatus") String refundStatus);
    int updateRefundStatus(@Param("refundSeq") String refundSeq, 
                           @Param("refundStatus") String refundStatus, 
                           @Param("resultCode") String resultCode,
                           @Param("resultMsg") String resultMsg,
                           @Param("responseBody") String responseBody,
                           @Param("updateTime") java.time.LocalDateTime updateTime);

    /**
     * 退款回调收口：按商户退款单号把 PROCESSING 推进到终态，影响 0 行即幂等命中（重推或已终态）。
     * 刻意不写 RESPONSE_BODY / RESULT_CODE —— 那两列属申请方向，回调原文在 ALIPAY_PAY_CALLBACK_LOG.RAW_BODY。
     */
    int settleFromCallback(@Param("refundOrderNo") String refundOrderNo,
                           @Param("refundStatus") String refundStatus,
                           @Param("resultMsg") String resultMsg,
                           @Param("updateTime") java.time.LocalDateTime updateTime);

    /**
     * 扫一批可回查的退款明细（{@code PROCESSING} 且已过静默期），供退款回查补偿用。
     *
     * <p>本表<b>没有退避列</b>（无 {@code NEXT_RETRY_TIME} / {@code RETRY_COUNT} / {@code LAST_REQUEST_TIME}），
     * 因此节流只能靠 {@code UPDATE_TIME} 闸门，长期查不出终态的行由 {@code scanDays} 窗口自然滚出扫描集。
     * <b>与 pay-sign 侧 {@code PayRefundDetailMapper.selectCompensableRefundQuery} 的退避机制刻意有偏差</b>，
     * 理由见 {@code RefundQueryCompensationService} 类注释，NEVER 因为「那边有退避列」就来给本表加列。
     *
     * @param scanDays     只回查最近 N 天创建的退款
     * @param staleMinutes 距上次更新至少多少分钟才回查，避开正常同步应答与回调的时间窗
     * @param limit        单轮条数上限，在 SQL 里限流、不在 Java 里截断
     */
    java.util.List<AlipayRefundLog> selectCompensableRefundQuery(@Param("scanDays") int scanDays,
                                                                @Param("staleMinutes") int staleMinutes,
                                                                @Param("limit") int limit);
}
