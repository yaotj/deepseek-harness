package com.chinasofti.huateng.collectpay.mapper;

import com.chinasofti.huateng.collectpay.entity.RefundOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefundOrderMapper {
    /**
     * 根据退款单号查询。
     */
    RefundOrder selectByRefundNo(@Param("refundNo") String refundNo);

    /**
     * 根据原支付订单号查询。
     */
    RefundOrder selectByPayOderNo(@Param("payOderNo") String orderNo);

    /**
     * 插入退款记录。
     */
    int insert(RefundOrder record);

    /**
     * 更新退款状态。
     */
    int updateRefundStatus(RefundOrder record);
}
