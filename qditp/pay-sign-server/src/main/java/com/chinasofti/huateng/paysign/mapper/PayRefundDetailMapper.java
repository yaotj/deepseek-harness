package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PayRefundDetail;
import com.chinasofti.huateng.paysign.page.PayRefundOrderView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PayRefundDetailMapper {
    int insert(PayRefundDetail record);

    int markRequesting(@Param("refundOrderNo") String refundOrderNo,
                       @Param("txnDate") String txnDate,
                       @Param("requestBody") String requestBody);

    int updateRequestResult(PayRefundDetail record);


    int countInFlightByOrderNo(@Param("orderNo") String orderNo);


    List<PayRefundDetail> selectCompensableRefundQuery(@Param("txnDateFrom") String txnDateFrom,
                                                       @Param("staleMinutes") int staleMinutes,
                                                       @Param("limit") int limit);

    int finishFromQuery(PayRefundDetail record);

    int delayNextRefundQuery(@Param("refundOrderNo") String refundOrderNo,
                             @Param("txnDate") String txnDate,
                             @Param("delaySeconds") int delaySeconds);


    int exhaustFromQuery(@Param("refundOrderNo") String refundOrderNo,
                         @Param("txnDate") String txnDate,
                         @Param("payCenterCode") String payCenterCode,
                         @Param("payCenterMsg") String payCenterMsg);


    PayRefundDetail selectByRefundOrderNo(@Param("refundOrderNo") String refundOrderNo);


    List<String> selectDriftedRefundSummary(@Param("txnDateFrom") String txnDateFrom,
                                            @Param("limit") int limit);


    List<String> selectOrphanRefundOrders(@Param("txnDateFrom") String txnDateFrom,
                                          @Param("limit") int limit);


    List<PayRefundOrderView> selectViewsByOrderNos(@Param("orderNos") List<String> orderNos);
}
