package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PayRefundDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PayRefundDetailMapper {
    /**
     * 新增退款明细。
     */
    int insert(PayRefundDetail record);

    /**
     * 调用支付中心前标记退款处理中并增加请求次数。
     */
    int markRequesting(@Param("refundOrderNo") String refundOrderNo,
                       @Param("txnDate") String txnDate,
                       @Param("requestBody") String requestBody);

    /**
     * 回写本次退款请求结果。
     */
    int updateRequestResult(PayRefundDetail record);
}
