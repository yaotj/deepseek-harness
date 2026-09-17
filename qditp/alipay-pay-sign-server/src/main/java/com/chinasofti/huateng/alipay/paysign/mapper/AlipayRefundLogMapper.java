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
}
