package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlipayRefundLogMapper {
    AlipayRefundLog selectByRefundOrderNo(@Param("refundOrderNo") String refundOrderNo);
    AlipayRefundLog selectByOrderNo(@Param("orderNo") String orderNo);

    /** 冲突兜底用：只取在途行。不能复用 selectByOrderNo——同订单可有多笔终态退款，单值返回会抛 TooManyResultsException。 */
    AlipayRefundLog selectProcessingByOrderNo(@Param("orderNo") String orderNo);

    int insert(AlipayRefundLog refundLog);

    int countByOrderNoAndStatus(@Param("orderNo") String orderNo, @Param("refundStatus") String refundStatus);
    int updateRefundStatus(@Param("refundSeq") String refundSeq, 
                           @Param("refundStatus") String refundStatus, 
                           @Param("resultCode") String resultCode,
                           @Param("resultMsg") String resultMsg,
                           @Param("responseBody") String responseBody,
                           @Param("updateTime") java.time.LocalDateTime updateTime);


    int settleFromCallback(@Param("refundOrderNo") String refundOrderNo,
                           @Param("refundStatus") String refundStatus,
                           @Param("resultMsg") String resultMsg,
                           @Param("updateTime") java.time.LocalDateTime updateTime);


    java.util.List<AlipayRefundLog> selectCompensableRefundQuery(@Param("scanDays") int scanDays,
                                                                @Param("staleMinutes") int staleMinutes,
                                                                @Param("limit") int limit);
}
