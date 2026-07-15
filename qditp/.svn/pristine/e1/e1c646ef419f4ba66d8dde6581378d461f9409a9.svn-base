package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AlipayPayLogMapper {
    AlipayPayLog selectByOrderNo(@Param("orderNo") String orderNo);
    AlipayPayLog selectByPaySeq(@Param("paySeq") String paySeq);
    int insert(AlipayPayLog payLog);
    int updatePayStatus(@Param("paySeq") String paySeq, @Param("payStatus") String payStatus, @Param("updateTime") java.time.LocalDateTime updateTime);
    int updatePayNotify(@Param("orderNo") String orderNo, @Param("payStatus") String payStatus, @Param("tradeNo") String tradeNo, @Param("payAmount") String payAmount, @Param("updateTime") java.time.LocalDateTime updateTime);
    int updateRefundSummary(@Param("orderNo") String orderNo, @Param("refundAmount") String refundAmount, @Param("refundStatus") String refundStatus);

    /**
     * 查询支付宝出行订单列表。
     */
    List<AlipayPayLog> selectAlipayPayLogList(@Param("thirdUserId") String thirdUserId,
                                              @Param("cardId") String cardId,
                                              @Param("payStatus") String payStatus,
                                              @Param("startTime") String startTime,
                                              @Param("endTime") String endTime,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    /**
     * 查询支付宝出行订单总数。
     */
    int countAlipayPayLogList(@Param("thirdUserId") String thirdUserId,
                              @Param("cardId") String cardId,
                              @Param("payStatus") String payStatus,
                              @Param("startTime") String startTime,
                              @Param("endTime") String endTime);
}
