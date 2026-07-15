package com.chinasofti.huateng.gatetxnpay.mapper;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GateTxnPayMapper {
    /**
     * 新增过闸扣费订单。
     */
    int insert(GateTxnPay record);

    /**
     * 按交易业务键查询订单，用于闸机重复通知幂等处理。
     */
    GateTxnPay selectByBizKey(@Param("cardId") String cardId,
                              @Param("trxType") String trxType,
                              @Param("outTime") String outTime,
                              @Param("ticketTransSeq") String ticketTransSeq,
                              @Param("deviceId") String deviceId,
                              @Param("txnDate") String txnDate);

    /**
     * 更新扣费当前状态。
     */
    int updateStatus(@Param("orderNo") String orderNo,
                     @Param("txnDate") String txnDate,
                     @Param("debitStatus") String debitStatus,
                     @Param("remark") String remark);
}
