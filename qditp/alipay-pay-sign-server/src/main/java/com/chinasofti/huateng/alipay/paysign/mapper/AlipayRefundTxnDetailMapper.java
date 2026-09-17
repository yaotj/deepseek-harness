package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundTxnDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code ALIPAY_REFUND_TXN_DETAIL} 数据访问，形态对齐 pay-sign-server 的 {@code PayRefundDetailMapper}。
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

    /** 出网前留痕：请求次数 +1、状态置 {@code PROCESSING}、落本次请求报文快照。 */
    int markRequesting(@Param("refundOrderNo") String refundOrderNo,
                       @Param("txnDate") String txnDate,
                       @Param("requestBody") String requestBody);

    /** 回写支付中心退款受理结果，我方应答码与支付中心应答码分列存放。 */
    int updateRequestResult(AlipayRefundTxnDetail record);
}
