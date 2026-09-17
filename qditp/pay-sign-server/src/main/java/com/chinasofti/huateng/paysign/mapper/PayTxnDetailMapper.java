package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PayTxnDetailMapper {
    int insert(PayTxnDetail record);

    PayTxnDetail selectByOrderNo(@Param("orderNo") String orderNo);

    /** 批量按订单号查询支付明细（用于 IF8A-05 双源合并）。 */
    List<PayTxnDetail> selectByOrderNos(@Param("orderNos") List<String> orderNos);

    int markRequesting(@Param("orderNo") String orderNo);

    int updateRequestResult(PayTxnDetail record);

    int updatePayCallback(PayTxnDetail record);

    /** 按 PAY_REFUND_DETAIL 重算原支付订单的已退款金额与退款状态。 */
    int updateRefundSummary(@Param("orderNo") String orderNo);
}
