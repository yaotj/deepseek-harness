package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PayTxnDetailMapper {
    int insert(PayTxnDetail record);

    PayTxnDetail selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 批量按订单号查询支付明细（用于 IF8A-05 双源合并）。
     */
    List<PayTxnDetail> selectByOrderNos(@Param("orderNos") List<String> orderNos);

    int markRequesting(@Param("orderNo") String orderNo);

    int updateRequestResult(PayTxnDetail record);

    int updatePayCallback(PayTxnDetail record);

    /**
     * 按 PAY_REFUND_DETAIL 重算原支付订单的已退款金额与退款状态。
     *
     * <p>只传 orderNo：金额与状态都由 SQL 从明细表汇总，**NEVER** 由调用方传入增量——
     * 入参没有幂等键，传增量就意味着重复执行会重复累加（详见 mapper XML 内注释）。</p>
     */
    int updateRefundSummary(@Param("orderNo") String orderNo);
}
