package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PayTxnDetailMapper {
    int insert(PayTxnDetail record);

    PayTxnDetail selectByOrderNo(@Param("orderNo") String orderNo);

    List<PayTxnDetail> selectByOrderNos(@Param("orderNos") List<String> orderNos);

    int markRequesting(@Param("orderNo") String orderNo);

    int updateRequestResult(PayTxnDetail record);

    int updatePayCallback(PayTxnDetail record);

    int updateRefundSummary(@Param("orderNo") String orderNo);

    List<PayTxnDetail> selectCompensablePayStatus(@Param("txnDateFrom") String txnDateFrom,
                                                  @Param("staleMinutes") int staleMinutes,
                                                  @Param("limit") int limit);
}
