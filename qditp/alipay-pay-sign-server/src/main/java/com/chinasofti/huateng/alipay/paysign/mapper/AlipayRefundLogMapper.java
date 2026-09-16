package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlipayRefundLogMapper {
    AlipayRefundLog selectByRefundOrderNo(@Param("refundOrderNo") String refundOrderNo);
    AlipayRefundLog selectByOrderNo(@Param("orderNo") String orderNo);
    int insert(AlipayRefundLog refundLog);

    /**
     * 统计同一原订单下处于指定退款状态的明细条数。
     *
     * <p>供 {@code requestRefund} 做前置短路：同一订单只要还有 {@code PROCESSING} 的退款
     * （已把请求发给支付中心、结果尚未收口），就 <b>MUST 拒绝新的退款申请</b>。
     * 本表没有调用方提供的幂等键（{@code REFUND_ORDER_NO} 是我方每次新生成的），
     * 因此这条状态短路是唯一能挡住「远端已受理、本地未回写」时重复退款的防线。</p>
     */
    int countByOrderNoAndStatus(@Param("orderNo") String orderNo, @Param("refundStatus") String refundStatus);
    int updateRefundStatus(@Param("refundSeq") String refundSeq, 
                           @Param("refundStatus") String refundStatus, 
                           @Param("resultCode") String resultCode,
                           @Param("resultMsg") String resultMsg,
                           @Param("responseBody") String responseBody,
                           @Param("updateTime") java.time.LocalDateTime updateTime);
}
