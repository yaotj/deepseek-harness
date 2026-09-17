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
     * 补偿扫表：捞出停在 {@code PROCESSING} 且到点可回查的退款明细。
     *
     * @param txnDateFrom  交易日期下界（{@code yyyyMMdd}），既做分区裁剪也做「扫多久以前的单」的上限
     * @param staleMinutes 距最近一次发起退款至少多少分钟才回查，避开正常同步应答的时间窗
     * @param limit        单轮上限
     */
    List<PayRefundDetail> selectCompensableRefundQuery(@Param("txnDateFrom") String txnDateFrom,
                                                       @Param("staleMinutes") int staleMinutes,
                                                       @Param("limit") int limit);

    /** 回查确认为终态后收口（CAS：仅 {@code PROCESSING} 可推进）。 */
    int finishFromQuery(PayRefundDetail record);

    /** 回查未得终态时只推下次回查时间（CAS：仅 {@code PROCESSING} 可推进）。 */
    int delayNextRefundQuery(@Param("refundOrderNo") String refundOrderNo,
                             @Param("txnDate") String txnDate,
                             @Param("delaySeconds") int delaySeconds);

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
