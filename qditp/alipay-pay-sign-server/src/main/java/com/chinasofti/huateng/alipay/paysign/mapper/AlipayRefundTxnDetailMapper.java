package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundTxnDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


@Mapper
public interface AlipayRefundTxnDetailMapper {

    int insert(AlipayRefundTxnDetail record);

    AlipayRefundTxnDetail selectByRefundOrderNo(@Param("refundOrderNo") String refundOrderNo);

    List<AlipayRefundTxnDetail> selectByOrderNo(@Param("orderNo") String orderNo);

    Integer sumSuccessRefundAmount(@Param("orderNo") String orderNo);

    int countByOrderNoAndStatus(@Param("orderNo") String orderNo,
                                @Param("refundStatus") String refundStatus);


    int markRequesting(@Param("refundOrderNo") String refundOrderNo,
                       @Param("txnDate") String txnDate);

    int updateRequestResult(AlipayRefundTxnDetail record);


    int settleFromCallback(@Param("refundOrderNo") String refundOrderNo,
                           @Param("refundStatus") String refundStatus,
                           @Param("remark") String remark);


    List<AlipayRefundTxnDetail> selectCompensableRefundQuery(@Param("scanDays") int scanDays,
                                                            @Param("staleMinutes") int staleMinutes,
                                                            @Param("limit") int limit);


    int delayNextRefundQuery(@Param("refundOrderNo") String refundOrderNo,
                             @Param("delaySeconds") int delaySeconds);


    List<AlipayRefundTxnDetail> selectPageForOps(@Param("refundOrderNo") String refundOrderNo,
                                                 @Param("orderNo") String orderNo,
                                                 @Param("refundStatus") String refundStatus,
                                                 @Param("txnDateBegin") String txnDateBegin,
                                                 @Param("txnDateEnd") String txnDateEnd,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    int countPageForOps(@Param("refundOrderNo") String refundOrderNo,
                        @Param("orderNo") String orderNo,
                        @Param("refundStatus") String refundStatus,
                        @Param("txnDateBegin") String txnDateBegin,
                        @Param("txnDateEnd") String txnDateEnd);
}
