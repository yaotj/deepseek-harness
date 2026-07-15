package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PayTxnDetailMapper {
    int insert(PayTxnDetail record);

    PayTxnDetail selectByOrderNo(@Param("orderNo") String orderNo);

    int markRequesting(@Param("orderNo") String orderNo);

    int updateRequestResult(PayTxnDetail record);

    int updatePayCallback(PayTxnDetail record);

    /**
     * 退款成功后累加原支付订单已退款金额并刷新退款状态。
     */
    int updateRefundSummary(@Param("orderNo") String orderNo,
                            @Param("refundAmount") Integer refundAmount,
                            @Param("refundStatus") String refundStatus);
}
