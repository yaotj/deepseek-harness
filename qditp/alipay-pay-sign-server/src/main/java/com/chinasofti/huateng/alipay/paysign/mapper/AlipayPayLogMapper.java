package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.model.alipaytrip.AlipayPayLogDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AlipayPayLogMapper {
    AlipayPayLog selectByOrderNo(@Param("orderNo") String orderNo);
    AlipayPayLog selectByPaySeq(@Param("paySeq") String paySeq);
    int insert(AlipayPayLog payLog);
    int updatePayStatus(@Param("paySeq") String paySeq, @Param("payStatus") String payStatus);
    int updatePayNotify(@Param("orderNo") String orderNo, @Param("payStatus") String payStatus, @Param("tradeNo") String tradeNo, @Param("transTime") String transTime, @Param("payAmount") String payAmount, @Param("resultCode") String resultCode, @Param("resultMsg") String resultMsg);

    int updatePayQueryResultIfNotSuccess(@Param("orderNo") String orderNo,
                                        @Param("payStatus") String payStatus,
                                        @Param("tradeNo") String tradeNo,
                                        @Param("transTime") String transTime,
                                        @Param("payAmount") String payAmount,
                                        @Param("resultCode") String resultCode,
                                        @Param("resultMsg") String resultMsg);
    int updateRefundSummary(@Param("orderNo") String orderNo);


    List<AlipayPayLog> selectAlipayPayLogList(@Param("thirdUserId") String thirdUserId,
                                              @Param("startTime") String startTime,
                                              @Param("endTime") String endTime,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit,
                                              @Param("debitRequestResult") String debitRequestResult,
                                              @Param("invoice") String invoice);


    int countAlipayPayLogList(@Param("thirdUserId") String thirdUserId,
                              @Param("startTime") String startTime,
                              @Param("endTime") String endTime,
                              @Param("debitRequestResult") String debitRequestResult,
                              @Param("invoice") String invoice);


    AlipayPayLogDTO selectByEntryId(@Param("entryId") String entryId);


    AlipayPayLogDTO selectByExitId(@Param("exitId") String exitId);

    int countUnsettledByCardId(@Param("cardId") String cardId);
}
