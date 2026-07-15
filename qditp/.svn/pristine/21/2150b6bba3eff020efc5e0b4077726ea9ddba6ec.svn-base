package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlipayRefundLogMapper {
    AlipayRefundLog selectByRefundOrderNo(@Param("refundOrderNo") String refundOrderNo);
    AlipayRefundLog selectByOrderNo(@Param("orderNo") String orderNo);
    int insert(AlipayRefundLog refundLog);
    int updateRefundStatus(@Param("refundSeq") String refundSeq, @Param("refundStatus") String refundStatus, @Param("updateTime") java.time.LocalDateTime updateTime);
}
