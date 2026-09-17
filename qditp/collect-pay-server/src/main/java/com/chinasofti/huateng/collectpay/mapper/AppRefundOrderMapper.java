package com.chinasofti.huateng.collectpay.mapper;

import com.chinasofti.huateng.collectpay.entity.AppRefundOrder;
import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

/** BOM退款订单Mapper接口。 */
@Mapper
public interface AppRefundOrderMapper {

    /**
     * 根据退款单号查询退款订单信息。
     *
     * @param refundNo 退款单号
     * @return 退款订单实体对象，不存在返回null
     */
    AppRefundOrder selectByRefundNo(String refundNo);

    /**
     * 根据原支付订单号查询退款订单信息。
     *
     * @param payOrderNo 原支付订单号
     * @return 退款订单实体对象，不存在返回null
     */
    AppRefundOrder selectByPayOrderNo(String payOrderNo);

    /**
     * 插入新退款订单记录。
     *
     * @param refundOrder 退款订单实体对象
     * @return 影响的行数
     */
    int insert(AppRefundOrder refundOrder);

    /**
     * 根据退款单号更新退款订单信息。
     *
     * @param params 更新参数，必须包含refundNo字段
     * @return 影响的行数
     */
    int updateByRefundNo(Map<String, String> params);

    /**
     * 汇总某笔支付订单**已退款成功**的金额合计（单位：分）。
     *
     * @param payOrderNo 原支付订单号
     * @return 已成功退款金额合计（分），无成功退款时返回 0
     */
    long sumSuccessRefundAmount(String payOrderNo);
}
